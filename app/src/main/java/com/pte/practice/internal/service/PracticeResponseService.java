package com.pte.practice.internal.service;

import com.pte.attempt.ResponseConfidence;
import com.pte.itembank.PracticeQuestionService;
import com.pte.itembank.dto.response.PracticeQuestionContent;
import com.pte.itembank.dto.response.PracticeQuestionEvaluationContent;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.enums.PracticeScoringStatus;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.request.PracticeAnswerRequest;
import com.pte.practice.internal.dto.request.PracticeCapabilityManifest;
import com.pte.practice.internal.dto.response.PracticeCatalogTaskResponse;
import com.pte.practice.internal.dto.response.PracticeItemResultResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.practice.PracticeResponseMediaValidator;
import com.pte.scoring.ScoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the item boundary inside a practice session. The session service keeps
 * the workflow and idempotency rules; this service keeps item persistence and
 * state transitions together.
 */
@Service
public class PracticeResponseService {

    private final PracticeResponseRepository itemRepository;
    private final PracticeCatalogService catalogService;
    private final PracticeAnswerValidationService answerValidationService;
    private final PracticeResponseMediaValidator mediaValidator;
    private final PracticeQuestionService practiceQuestionService;
    private final JsonMapper jsonMapper;
    private final PracticeScoringStateService scoringStateService;
    private final PracticeAnswerScoringRouter scoringRouter;

    public PracticeResponseService(PracticeResponseRepository itemRepository,
            PracticeCatalogService catalogService, PracticeAnswerValidationService answerValidationService) {
        this(itemRepository, catalogService, answerValidationService, null, null, null, null, null, null);
    }

    /** Compatibility constructor for tests that exercise recording validation without media wiring. */
    public PracticeResponseService(PracticeResponseRepository itemRepository,
            PracticeCatalogService catalogService, PracticeAnswerValidationService answerValidationService,
            PracticeResponseMediaValidator mediaValidator) {
        this(itemRepository, catalogService, answerValidationService, mediaValidator, null, null, null, null, null);
    }

    @Autowired
    public PracticeResponseService(PracticeResponseRepository itemRepository,
            PracticeCatalogService catalogService, PracticeAnswerValidationService answerValidationService,
            PracticeResponseMediaValidator mediaValidator, PracticeQuestionService practiceQuestionService,
            JsonMapper jsonMapper, ScoringService scoringService, PracticeScoringStateService scoringStateService,
            PracticeScoringRequestFactory scoringRequestFactory) {
        this.itemRepository = itemRepository;
        this.catalogService = catalogService;
        this.answerValidationService = answerValidationService;
        this.mediaValidator = mediaValidator;
        this.practiceQuestionService = practiceQuestionService;
        this.jsonMapper = jsonMapper;
        this.scoringStateService = scoringStateService;
        this.scoringRouter = new PracticeAnswerScoringRouter(scoringService, scoringStateService,
                scoringRequestFactory);
    }

    public void createItemsIfNeeded(PracticeSession session) {
        if (session.getId() == null || !itemsFor(session).isEmpty()) {
            return;
        }

        List<PracticeCatalogTaskResponse> tasks = catalogService.resolveStartableTasks(
                session.getProductCode(), splitCsv(session.getSelectedTaskTypes()),
                new PracticeCapabilityManifest(splitCsv(session.getClientCapabilities()), null));
        List<PracticeResponse> items = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            PracticeCatalogTaskResponse task = tasks.get(index);
            PracticeResponse item = new PracticeResponse();
            item.setPracticeSessionId(session.getId());
            item.setOrderIndex(index);
            item.setTaskCode(task.code());
            item.setDisplayName(task.displayName());
            item.setSection(task.section());
            item.setRendererKey(task.rendererKey());
            item.setContractVersion(task.contractVersion());
            item.setAnswerSchemaVersion(task.answerSchemaVersion());
            pinQuestionContent(item, task.code());
            item.setStatus(PracticeResponseStatus.PENDING);
            items.add(item);
        }
        if (!items.isEmpty()) {
            itemRepository.saveAll(items);
        }
    }

    public List<PracticeResponse> itemsFor(PracticeSession session) {
        if (session.getId() == null) {
            return List.of();
        }
        List<PracticeResponse> items = itemRepository
                .findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(session.getId());
        return items == null ? List.of() : items;
    }

    public PracticeResponse findForUpdate(PracticeSession session, UUID itemPublicId) {
        return itemRepository.findWithLockByPublicIdAndPracticeSessionId(itemPublicId, session.getId())
                .orElseThrow(PracticeSessionNotFoundException::new);
    }

    public void requirePending(PracticeResponse item) {
        if (item.getStatus() != PracticeResponseStatus.PENDING) {
            throw notStartable();
        }
    }

    public void answer(PracticeSession session, PracticeResponse item, PracticeAnswerRequest request,
            CurrentUser caller, Instant now) {
        requirePending(item);
        answerValidationService.validate(item.getRendererKey(), item.getAnswerSchemaVersion(),
                request.payload(), request.confidence());
        validateRecordedMedia(session, item, request.payload(), caller);
        item.setSavedPayload(request.payload());
        item.setConfidence(request.confidence());
        scoringRouter.scoreOrQueue(session, item, now);
        item.setStatus(PracticeResponseStatus.ANSWERED);
        item.setAnsweredAt(now);
        itemRepository.saveAndFlush(item);
    }

    public void saveDraft(PracticeSession session, PracticeResponse item, String payload,
            ResponseConfidence confidence, CurrentUser caller) {
        requirePending(item);
        answerValidationService.validateDraft(item.getRendererKey(), item.getAnswerSchemaVersion(), payload, confidence);
        validateRecordedMedia(session, item, payload, caller);
        item.setSavedPayload(payload);
        item.setConfidence(confidence);
        itemRepository.saveAndFlush(item);
    }

    public void skip(PracticeResponse item, Instant now) {
        requirePending(item);
        item.setStatus(PracticeResponseStatus.SKIPPED);
        item.setSkippedAt(now);
        itemRepository.saveAndFlush(item);
    }

    public void completeIfFinished(PracticeSession session, Instant now) {
        List<PracticeResponse> items = itemsFor(session);
        if (!items.isEmpty() && items.stream().noneMatch(item -> item.getStatus() == PracticeResponseStatus.PENDING)) {
            session.setStatus(PracticeSessionStatus.COMPLETED);
            session.setCompletedAt(now);
        }
    }

    public boolean hasProgress(PracticeSession session) {
        return itemsFor(session).stream().anyMatch(item -> item.getStatus() == PracticeResponseStatus.ANSWERED
                || (item.getSavedPayload() != null && !item.getSavedPayload().isBlank()));
    }

    private PracticeSessionException notStartable() {
        return new PracticeSessionException(HttpStatus.CONFLICT,
                PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE,
                PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE_MESSAGE);
    }

    private void validateRecordedMedia(PracticeSession session, PracticeResponse item,
            String payload, CurrentUser caller) {
        if (!PracticeConstants.PRACTICE_RECORDING_RENDERER_KEYS.contains(item.getRendererKey())) {
            return;
        }
        if (mediaValidator == null) {
            throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME,
                    PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME_MESSAGE);
        }
        UUID mediaPublicId = answerValidationService.requireRecordedMediaPublicId(item.getRendererKey(), payload);
        mediaValidator.validatePracticeResponseAudio(mediaPublicId, session.getPublicId(), item.getPublicId(), caller);
    }

    /** Result of this item's scoring for the answer/poll response; null when scoring is not wired (unit tests). */
    public PracticeItemResultResponse resultOf(PracticeResponse item) {
        return scoringStateService == null ? null : scoringStateService.resultOf(item);
    }

    /** Repairs lost or stuck AI jobs of this session; called on every session read. */
    public void recoverScoring(PracticeSession session, Instant now) {
        if (scoringStateService == null || session.getId() == null) {
            return;
        }
        // Loaded under a lock so the recovery write sees the worker's latest state instead of a stale snapshot.
        List<PracticeResponse> scoring = itemRepository.findScoringForUpdate(session.getId());
        if (scoring != null) {
            scoring.forEach(item -> scoringStateService.recover(session, item, now));
        }
    }

    public void recoverScoring(PracticeSession session, PracticeResponse item, Instant now) {
        if (scoringStateService != null && item.getScoringStatus() == PracticeScoringStatus.SCORING) {
            scoringStateService.recover(session, item, now);
        }
    }

    /** Re-submits a FAILED item to the scorer; the caller holds the item lock. */
    public PracticeItemResultResponse retryScoring(PracticeSession session, PracticeResponse item, Instant now) {
        return scoringStateService.retry(session, item, now);
    }

    private Set<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void pinQuestionContent(PracticeResponse item, String taskCode) {
        if (practiceQuestionService == null) {
            return;
        }
        PracticeQuestionContent content = practiceQuestionService.findPublishedQuestion(taskCode)
                .orElseThrow(() -> new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                        PracticeConstants.PRACTICE_CONTENT_NOT_READY,
                        PracticeConstants.PRACTICE_CONTENT_NOT_READY_MESSAGE));
        item.setSourceQuestionPublicId(content.publicId());
        item.setPromptText(content.promptText());
        item.setMinWordCount(content.minWordCount());
        item.setMaxWordCount(content.maxWordCount());
        item.setAudioPromptRef(content.audioPromptRef());
        item.setImagePromptRef(content.imagePromptRef());
        PracticeQuestionEvaluationContent evaluationContent = practiceQuestionService
                .findPublishedQuestionForEvaluation(content.publicId())
                .orElseThrow(() -> new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                        PracticeConstants.PRACTICE_CONTENT_NOT_READY,
                        PracticeConstants.PRACTICE_CONTENT_NOT_READY_MESSAGE));
        item.setReferenceAnswerText(evaluationContent.referenceAnswerText());
        item.setCorrectAnswerText(evaluationContent.correctAnswerText());
        try {
            item.setOptionsJson(jsonMapper.writeValueAsString(content.options()));
            item.setObjectiveOptionsJson(jsonMapper.writeValueAsString(evaluationContent.options()));
        } catch (RuntimeException ex) {
            throw new PracticeSessionException(HttpStatus.SERVICE_UNAVAILABLE,
                    PracticeConstants.PRACTICE_CONTENT_NOT_READY,
                    PracticeConstants.PRACTICE_CONTENT_NOT_READY_MESSAGE);
        }
    }
}
