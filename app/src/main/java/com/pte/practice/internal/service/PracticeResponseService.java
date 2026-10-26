package com.pte.practice.internal.service;

import com.pte.attempt.ResponseConfidence;
import com.pte.itembank.PracticeQuestionService;
import com.pte.itembank.dto.response.PracticeQuestionContent;
import com.pte.itembank.dto.response.PracticeQuestionEvaluationContent;
import com.pte.itembank.dto.response.PracticeQuestionPin;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeMockTestItem;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.enums.PracticeScoringStatus;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionKind;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.domain.enums.PracticeTimingMode;
import com.pte.practice.internal.dto.request.PracticeAnswerRequest;
import com.pte.practice.internal.dto.request.PracticeCapabilityManifest;
import com.pte.practice.internal.dto.response.PracticeCatalogAvailability;
import com.pte.practice.internal.dto.response.PracticeCatalogTaskResponse;
import com.pte.practice.internal.dto.response.PracticeItemResultResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.practice.PracticeResponseMediaValidator;
import com.pte.scoring.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the item boundary inside a practice session. The session service keeps
 * the workflow and idempotency rules; this service keeps item persistence and
 * state transitions together.
 */
@Service
public class PracticeResponseService {

    private static final Logger log = LoggerFactory.getLogger(PracticeResponseService.class);

    private final PracticeResponseRepository itemRepository;
    private final PracticeCatalogService catalogService;
    private final PracticeAnswerValidationService answerValidationService;
    private final PracticeResponseMediaValidator mediaValidator;
    private final PracticeQuestionService practiceQuestionService;
    private final JsonMapper jsonMapper;
    private final PracticeScoringStateService scoringStateService;
    private final PracticeAnswerScoringRouter scoringRouter;
    private final PracticeQuestionPicker questionPicker;
    /** Null in focused tests: TIMED sessions then fall back to UNTIMED. */
    private final PracticeTimingService timingService;
    /** Set by Spring; null in focused tests that do not build mock-test sessions. */
    private PracticeMockTestService mockTestService;

    @Autowired(required = false)
    void setMockTestService(PracticeMockTestService mockTestService) {
        this.mockTestService = mockTestService;
    }

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

    /** Compatibility constructor for tests that predate question picking and timed sessions. */
    public PracticeResponseService(PracticeResponseRepository itemRepository,
            PracticeCatalogService catalogService, PracticeAnswerValidationService answerValidationService,
            PracticeResponseMediaValidator mediaValidator, PracticeQuestionService practiceQuestionService,
            JsonMapper jsonMapper, ScoringService scoringService, PracticeScoringStateService scoringStateService,
            PracticeScoringRequestFactory scoringRequestFactory) {
        this(itemRepository, catalogService, answerValidationService, mediaValidator, practiceQuestionService,
                jsonMapper, scoringService, scoringStateService, scoringRequestFactory, new PracticeQuestionPicker(),
                null);
    }

    @Autowired
    public PracticeResponseService(PracticeResponseRepository itemRepository,
            PracticeCatalogService catalogService, PracticeAnswerValidationService answerValidationService,
            PracticeResponseMediaValidator mediaValidator, PracticeQuestionService practiceQuestionService,
            JsonMapper jsonMapper, ScoringService scoringService, PracticeScoringStateService scoringStateService,
            PracticeScoringRequestFactory scoringRequestFactory, PracticeQuestionPicker questionPicker,
            PracticeTimingService timingService) {
        this.questionPicker = questionPicker;
        this.timingService = timingService;
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

    /**
     * Creates the session's items once, at begin: up to {@code questionCount} distinct questions per selected task
     * type (one for sessions started before V133), unseen questions first, with content frozen in one batch. A
     * TIMED session pins the active template's timing on every item; if the template no longer covers the chosen
     * task types, the session falls back to UNTIMED rather than getting stuck before its first item.
     */
    public void createItemsIfNeeded(PracticeSession session) {
        if (session.getId() == null || !itemsFor(session).isEmpty()) {
            return;
        }
        if (session.effectiveKind() == PracticeSessionKind.MOCK_TEST) {
            createMockTestItems(session);
            return;
        }

        List<PracticeCatalogTaskResponse> tasks = catalogService.resolveStartableTasks(
                session.getProductCode(), splitCsv(session.getSelectedTaskTypes()),
                new PracticeCapabilityManifest(splitCsv(session.getClientCapabilities()), null));
        Map<String, PracticeTimingService.Timing> timings = timingsFor(session, tasks);
        List<PracticeResponse> items = practiceQuestionService == null
                ? tasks.stream().map(task -> newItem(session, task)).toList()
                : pickAndPin(session, tasks, timings);
        for (int index = 0; index < items.size(); index++) {
            items.get(index).setOrderIndex(index);
        }
        if (!items.isEmpty()) {
            itemRepository.saveAll(items);
        }
    }

    /**
     * Items of a mock test come from its pinned question list, in the stored order. Unlike a normal session this is
     * tolerant: a question that was unpublished since the test was built, or a task type the client cannot run, is
     * left out with a warning instead of blocking the whole test, and each item gets its own timing (or none).
     */
    private void createMockTestItems(PracticeSession session) {
        List<PracticeMockTestItem> pinned = mockTestService.itemsOf(session.getMockTestId());
        Map<String, PracticeCatalogTaskResponse> tasks = new HashMap<>();
        catalogService.getCatalog().sections().forEach(section -> section.taskTypes()
                .forEach(task -> tasks.put(PracticeTimingService.normalize(task.code()), task)));
        Map<UUID, PracticeQuestionPin> pins = new HashMap<>();
        practiceQuestionService.findPublishedQuestionsForPinning(
                pinned.stream().map(PracticeMockTestItem::getQuestionPublicId).toList())
                .forEach(pin -> pins.put(pin.content().publicId(), pin));
        Map<String, PracticeTimingService.Timing> timings = session.effectiveTimingMode() == PracticeTimingMode.TIMED
                && timingService != null ? timingService.activeTimings() : Map.of();
        if (session.effectiveTimingMode() == PracticeTimingMode.TIMED && timings.isEmpty()) {
            session.setTimingMode(PracticeTimingMode.UNTIMED);
        }

        List<PracticeResponse> items = new ArrayList<>();
        for (PracticeMockTestItem entry : pinned) {
            PracticeCatalogTaskResponse task = tasks.get(PracticeTimingService.normalize(entry.getTaskCode()));
            PracticeQuestionPin pin = pins.get(entry.getQuestionPublicId());
            if (task == null || task.availability() != PracticeCatalogAvailability.RUNNABLE || pin == null) {
                log.warn("Mock test {} item {} ({}) is skipped: task runnable={}, question published={}",
                        session.getMockTestId(), entry.getOrderIndex(), entry.getTaskCode(),
                        task != null && task.availability() == PracticeCatalogAvailability.RUNNABLE, pin != null);
                continue;
            }
            PracticeResponse item = newItem(session, task);
            pinQuestionContent(item, pin);
            PracticeTimingService.Timing timing = timings.get(PracticeTimingService.normalize(entry.getTaskCode()));
            if (timing != null) {
                item.setPreListenSeconds(timing.preListenSeconds());
                item.setPreRecordSeconds(timing.preRecordSeconds());
                item.setResponseSeconds(timing.responseSeconds());
            }
            item.setOrderIndex(items.size());
            items.add(item);
        }
        if (items.isEmpty()) {
            throw contentNotReady();
        }
        itemRepository.saveAll(items);
    }

    private Map<String, PracticeTimingService.Timing> timingsFor(PracticeSession session,
            List<PracticeCatalogTaskResponse> tasks) {
        if (session.effectiveTimingMode() != PracticeTimingMode.TIMED) {
            return Map.of();
        }
        Map<String, PracticeTimingService.Timing> timings = timingService == null ? Map.of()
                : timingService.activeTimings();
        List<String> codes = tasks.stream().map(PracticeCatalogTaskResponse::code).toList();
        if (timingService == null || !timingService.covers(timings, codes)) {
            log.warn("Timed practice session {} has no template timing for {}; continuing untimed",
                    session.getPublicId(), codes);
            session.setTimingMode(PracticeTimingMode.UNTIMED);
            return Map.of();
        }
        return timings;
    }

    private List<PracticeResponse> pickAndPin(PracticeSession session, List<PracticeCatalogTaskResponse> tasks,
            Map<String, PracticeTimingService.Timing> timings) {
        int[] allocation = allocationFor(session, tasks.size());
        Set<UUID> seen = new HashSet<>(itemRepository.findSeenSourceQuestionIds(session.getStudentPublicId(),
                tasks.stream().map(PracticeCatalogTaskResponse::code).toList()));
        Map<PracticeCatalogTaskResponse, List<UUID>> pickedByTask = new LinkedHashMap<>();
        List<UUID> allPicked = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            PracticeCatalogTaskResponse task = tasks.get(index);
            if (allocation[index] <= 0) {
                continue; // a skill session with fewer questions than task types leaves the last types out
            }
            List<UUID> picked = questionPicker.pick(
                    practiceQuestionService.findPublishedCandidateIds(task.code()), seen, allocation[index]);
            if (picked.isEmpty() && session.effectiveKind() == PracticeSessionKind.TASK) {
                throw contentNotReady();
            }
            if (!picked.isEmpty()) {
                pickedByTask.put(task, picked);
                allPicked.addAll(picked);
            }
        }
        Map<UUID, PracticeQuestionPin> pins = new HashMap<>();
        practiceQuestionService.findPublishedQuestionsForPinning(allPicked)
                .forEach(pin -> pins.put(pin.content().publicId(), pin));

        List<PracticeResponse> items = new ArrayList<>();
        pickedByTask.forEach((task, picked) -> {
            PracticeTimingService.Timing timing = timings.get(PracticeTimingService.normalize(task.code()));
            for (UUID questionId : picked) {
                PracticeQuestionPin pin = pins.get(questionId);
                if (pin == null) {
                    // Unpublished between picking and pinning; the other picks still make a usable session.
                    continue;
                }
                PracticeResponse item = newItem(session, task);
                pinQuestionContent(item, pin);
                if (timing != null) {
                    item.setPreListenSeconds(timing.preListenSeconds());
                    item.setPreRecordSeconds(timing.preRecordSeconds());
                    item.setResponseSeconds(timing.responseSeconds());
                }
                items.add(item);
            }
        });
        if (items.isEmpty()) {
            throw contentNotReady();
        }
        return items;
    }

    /** Questions to pick per task type: the chosen count each, or a skill session's total split across its types. */
    private int[] allocationFor(PracticeSession session, int taskCount) {
        int count = session.getQuestionCount() == null
                ? PracticeConstants.PRACTICE_LEGACY_QUESTION_COUNT : session.getQuestionCount();
        if (session.effectiveKind() == PracticeSessionKind.SKILL) {
            return PracticeQuestionAllocation.distribute(count, taskCount);
        }
        int[] each = new int[taskCount];
        java.util.Arrays.fill(each, count);
        return each;
    }

    private PracticeResponse newItem(PracticeSession session, PracticeCatalogTaskResponse task) {
        PracticeResponse item = new PracticeResponse();
        item.setPracticeSessionId(session.getId());
        item.setTaskCode(task.code());
        item.setDisplayName(task.displayName());
        item.setSection(task.section());
        item.setRendererKey(task.rendererKey());
        item.setContractVersion(task.contractVersion());
        item.setAnswerSchemaVersion(task.answerSchemaVersion());
        item.setStatus(PracticeResponseStatus.PENDING);
        return item;
    }

    private PracticeSessionException contentNotReady() {
        return new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                PracticeConstants.PRACTICE_CONTENT_NOT_READY,
                PracticeConstants.PRACTICE_CONTENT_NOT_READY_MESSAGE);
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
        if (item.getStatus() != PracticeResponseStatus.PENDING
                && item.getStatus() != PracticeResponseStatus.SKIPPED) {
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
        item.setSkippedAt(null);
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

    private void pinQuestionContent(PracticeResponse item, PracticeQuestionPin pin) {
        PracticeQuestionContent content = pin.content();
        item.setSourceQuestionPublicId(content.publicId());
        item.setPromptText(content.promptText());
        item.setMinWordCount(content.minWordCount());
        item.setMaxWordCount(content.maxWordCount());
        item.setAudioPromptRef(content.audioPromptRef());
        item.setImagePromptRef(content.imagePromptRef());
        PracticeQuestionEvaluationContent evaluationContent = pin.evaluation();
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
