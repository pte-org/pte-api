package com.pte.practice.internal.service;

import com.pte.attempt.ResponseConfidence;
import com.pte.itembank.PracticeQuestionService;
import com.pte.itembank.dto.response.PracticeQuestionContent;
import com.pte.itembank.dto.response.PracticeQuestionEvaluationContent;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.request.PracticeAnswerRequest;
import com.pte.practice.internal.dto.request.PracticeCapabilityManifest;
import com.pte.practice.internal.dto.response.PracticeCatalogTaskResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.practice.PracticeResponseMediaValidator;
import com.pte.scoring.ScoringService;
import com.pte.scoring.PracticeFormEvaluation;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the item boundary inside a practice session. The session service keeps
 * the workflow and idempotency rules; this service keeps item persistence and
 * state transitions together.
 */
@Service
public class PracticeSessionItemService {

    private final PracticeSessionItemRepository itemRepository;
    private final PracticeCatalogService catalogService;
    private final PracticeAnswerValidationService answerValidationService;
    private final PracticeResponseMediaValidator mediaValidator;
    private final PracticeQuestionService practiceQuestionService;
    private final JsonMapper jsonMapper;
    private final PracticeRubricBindingService rubricBindingService;
    private final ScoringService scoringService;
    private final PracticeSpeechScoringDispatcher speechScoringDispatcher;

    public PracticeSessionItemService(PracticeSessionItemRepository itemRepository,
            PracticeCatalogService catalogService, PracticeAnswerValidationService answerValidationService) {
        this(itemRepository, catalogService, answerValidationService, null, null, null, null, null, null);
    }

    /** Compatibility constructor for tests that exercise recording validation without media wiring. */
    public PracticeSessionItemService(PracticeSessionItemRepository itemRepository,
            PracticeCatalogService catalogService, PracticeAnswerValidationService answerValidationService,
            PracticeResponseMediaValidator mediaValidator) {
        this(itemRepository, catalogService, answerValidationService, mediaValidator, null, null, null, null, null);
    }

    @Autowired
    public PracticeSessionItemService(PracticeSessionItemRepository itemRepository,
            PracticeCatalogService catalogService, PracticeAnswerValidationService answerValidationService,
            PracticeResponseMediaValidator mediaValidator, PracticeQuestionService practiceQuestionService,
            JsonMapper jsonMapper, PracticeRubricBindingService rubricBindingService, ScoringService scoringService,
            PracticeSpeechScoringDispatcher speechScoringDispatcher) {
        this.itemRepository = itemRepository;
        this.catalogService = catalogService;
        this.answerValidationService = answerValidationService;
        this.mediaValidator = mediaValidator;
        this.practiceQuestionService = practiceQuestionService;
        this.jsonMapper = jsonMapper;
        this.rubricBindingService = rubricBindingService;
        this.scoringService = scoringService;
        this.speechScoringDispatcher = speechScoringDispatcher;
    }

    public void createItemsIfNeeded(PracticeSession session) {
        if (session.getId() == null || !itemsFor(session).isEmpty()) {
            return;
        }

        List<PracticeCatalogTaskResponse> tasks = catalogService.resolveStartableTasks(
                session.getProductCode(), splitCsv(session.getSelectedTaskTypes()),
                new PracticeCapabilityManifest(splitCsv(session.getClientCapabilities()), null));
        List<PracticeSessionItem> items = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            PracticeCatalogTaskResponse task = tasks.get(index);
            PracticeSessionItem item = new PracticeSessionItem();
            item.setPracticeSessionId(session.getId());
            item.setOrderIndex(index);
            item.setTaskCode(task.code());
            item.setDisplayName(task.displayName());
            item.setSection(task.section());
            item.setRendererKey(task.rendererKey());
            item.setContractVersion(task.contractVersion());
            item.setAnswerSchemaVersion(task.answerSchemaVersion());
            pinQuestionContent(item, task.code());
            item.setStatus(PracticeSessionItemStatus.PENDING);
            items.add(item);
        }
        if (!items.isEmpty()) {
            itemRepository.saveAll(items);
        }
    }

    public List<PracticeSessionItem> itemsFor(PracticeSession session) {
        if (session.getId() == null) {
            return List.of();
        }
        List<PracticeSessionItem> items = itemRepository
                .findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(session.getId());
        return items == null ? List.of() : items;
    }

    public PracticeSessionItem findForUpdate(PracticeSession session, UUID itemPublicId) {
        return itemRepository.findWithLockByPublicIdAndPracticeSessionId(itemPublicId, session.getId())
                .orElseThrow(PracticeSessionNotFoundException::new);
    }

    public void requirePending(PracticeSessionItem item) {
        if (item.getStatus() != PracticeSessionItemStatus.PENDING) {
            throw notStartable();
        }
    }

    public void answer(PracticeSession session, PracticeSessionItem item, PracticeAnswerRequest request,
            CurrentUser caller, Instant now) {
        requirePending(item);
        answerValidationService.validate(item.getRendererKey(), item.getAnswerSchemaVersion(),
                request.payload(), request.confidence());
        validateRecordedMedia(session, item, request.payload(), caller);
        item.setSavedPayload(request.payload());
        item.setConfidence(request.confidence());
        evaluateObjective(item, now);
        evaluateForm(item);
        item.setStatus(PracticeSessionItemStatus.ANSWERED);
        item.setAnsweredAt(now);
        itemRepository.saveAndFlush(item);
        dispatchPracticeSpeechScoring(session, item);
    }

    public void saveDraft(PracticeSession session, PracticeSessionItem item, String payload,
            ResponseConfidence confidence, CurrentUser caller) {
        requirePending(item);
        answerValidationService.validateDraft(item.getRendererKey(), item.getAnswerSchemaVersion(), payload, confidence);
        validateRecordedMedia(session, item, payload, caller);
        item.setSavedPayload(payload);
        item.setConfidence(confidence);
        itemRepository.saveAndFlush(item);
    }

    public void skip(PracticeSessionItem item, Instant now) {
        requirePending(item);
        item.setStatus(PracticeSessionItemStatus.SKIPPED);
        item.setSkippedAt(now);
        itemRepository.saveAndFlush(item);
    }

    public void completeIfFinished(PracticeSession session, Instant now) {
        List<PracticeSessionItem> items = itemsFor(session);
        if (!items.isEmpty() && items.stream().noneMatch(item -> item.getStatus() == PracticeSessionItemStatus.PENDING)) {
            session.setStatus(PracticeSessionStatus.COMPLETED);
            session.setCompletedAt(now);
        }
    }

    public boolean hasProgress(PracticeSession session) {
        return itemsFor(session).stream().anyMatch(item -> item.getStatus() == PracticeSessionItemStatus.ANSWERED
                || (item.getSavedPayload() != null && !item.getSavedPayload().isBlank()));
    }

    private PracticeSessionException notStartable() {
        return new PracticeSessionException(HttpStatus.CONFLICT,
                PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE,
                PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE_MESSAGE);
    }

    private void validateRecordedMedia(PracticeSession session, PracticeSessionItem item,
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

    private void evaluateObjective(PracticeSessionItem item, Instant evaluatedAt) {
        if (scoringService == null || !usesScoringProfile(item, "OBJECTIVE")) {
            return;
        }
        // Items created before the answer-key snapshot migration cannot be
        // scored safely. Fail closed instead of persisting an incorrect zero.
        if (item.getObjectiveOptionsJson() == null || item.getObjectiveOptionsJson().isBlank()) {
            throw deterministicScoringUnavailable();
        }
        String canonicalPayload = objectivePayload(item);
        if (canonicalPayload == null) {
            throw deterministicScoringUnavailable();
        }
        var result = scoringService.evaluatePracticeObjective(item.getTaskCode(), canonicalPayload,
                item.getCorrectAnswerText(), item.getObjectiveOptionsJson());
        if (result.isEmpty()) {
            throw deterministicScoringUnavailable();
        }
        result.ifPresent(value -> {
            item.setObjectiveScore(value.rawScore());
            item.setObjectiveEvaluatedAt(evaluatedAt);
            item.setObjectiveEvaluatorVersion(value.evaluatorVersion());
            item.setObjectiveResultHash(value.resultHash());
            item.setObjectiveEvidenceIdentityHash(value.evidenceIdentityHash());
        });
    }

    /** Converts the public renderer payload into the canonical form expected by objective scorers. */
    private String objectivePayload(PracticeSessionItem item) {
        String payload = item.getSavedPayload();
        if (jsonMapper == null || payload == null || payload.isBlank()) {
            return payload;
        }
        try {
            JsonNode root = jsonMapper.readTree(payload);
            return switch (item.getRendererKey()) {
                case "MC_READING_SINGLE_V1", "MC_LISTENING_SINGLE_V1", "SELECT_MISSING_WORD_V1",
                        "HIGHLIGHT_CORRECT_SUMMARY_V1" -> root.path("selectedOption").asText(payload);
                case "MC_READING_MULTIPLE_V1", "MC_LISTENING_MULTIPLE_V1" -> joinTextArray(root.path("selectedOptions"), payload);
                case "RE_ORDER_PARAGRAPHS_V1" -> joinTextArray(root.path("order"), payload);
                case "FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1" -> joinBlankSelections(root.path("placements"),
                        item.getObjectiveOptionsJson(), false, payload);
                case "FILL_IN_THE_BLANKS_DROPDOWN_V1" -> joinBlankSelections(root.path("selections"),
                        item.getObjectiveOptionsJson(), true, payload);
                default -> payload;
            };
        } catch (RuntimeException exception) {
            return payload;
        }
    }

    /**
     * Converts the renderer's word values into the stable option indexes used
     * by the deterministic scorer. Blank positions remain positional, so an
     * unanswered middle or trailing blank is preserved as an empty entry.
     */
    private String joinBlankSelections(JsonNode selections, String optionsJson, boolean groupedByBlank,
            String fallback) {
        if (selections == null || !selections.isObject() || optionsJson == null || optionsJson.isBlank()) {
            return fallback;
        }
        List<PracticeEvaluationOption> options;
        try {
            options = jsonMapper.readValue(optionsJson, new TypeReference<List<PracticeEvaluationOption>>() {
            });
        } catch (RuntimeException exception) {
            return fallback;
        }
        int blankCount = options.stream()
                .mapToInt(option -> groupedByBlank ? valueOrMinusOne(option.blankIndex())
                        : valueOrMinusOne(option.correctGapIndex()))
                .max()
                .orElse(-1) + 1;
        if (blankCount <= 0) {
            return fallback;
        }
        List<String> orderIndexes = new ArrayList<>(blankCount);
        for (int blankIndex = 0; blankIndex < blankCount; blankIndex++) {
            final int gapIndex = blankIndex;
            String selectedText = selections.path(String.valueOf(gapIndex)).asText("");
            if (selectedText.isBlank()) {
                orderIndexes.add("");
                continue;
            }
            List<PracticeEvaluationOption> matches = options.stream()
                    .filter(option -> sameWord(option.text(), selectedText))
                    .filter(option -> !groupedByBlank || Integer.valueOf(gapIndex).equals(option.blankIndex()))
                    .toList();
            // The renderer currently submits text values. Duplicate text in a
            // word bank/group would make that payload ambiguous, so fail closed
            // instead of awarding the index of an arbitrary matching option.
            if (matches.size() != 1) {
                return null;
            }
            orderIndexes.add(String.valueOf(matches.get(0).orderIndex()));
        }
        return String.join(",", orderIndexes);
    }

    private boolean sameWord(String left, String right) {
        return left != null && right != null
                && left.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)
                        .equals(right.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
    }

    private int valueOrMinusOne(Integer value) {
        return value == null ? -1 : value;
    }

    private String joinTextArray(JsonNode node, String fallback) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return String.join(",", values);
    }

    private void evaluateForm(PracticeSessionItem item) {
        if (scoringService == null || !usesScoringProfile(item, "AI_TEXT")) {
            return;
        }
        if (jsonMapper == null || item.getSavedPayload() == null) {
            throw deterministicScoringUnavailable();
        }
        try {
            String responseText = jsonMapper.readTree(item.getSavedPayload()).path("text").asText(null);
            var result = scoringService.evaluatePracticeForm(item.getTaskCode(), responseText,
                    item.getMinWordCount(), item.getMaxWordCount());
            if (result.isEmpty()) {
                throw deterministicScoringUnavailable();
            }
            result.ifPresent(value -> applyFormResult(item, value));
        } catch (PracticeSessionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw deterministicScoringUnavailable();
        }
    }

    private boolean usesScoringProfile(PracticeSessionItem item, String profileKey) {
        try {
            var runtime = TaskRuntimeProfileRegistry.descriptorFor(item.getTaskCode());
            return runtime != null && profileKey.equals(runtime.scoringProfileKey());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private PracticeSessionException deterministicScoringUnavailable() {
        return new PracticeSessionException(HttpStatus.SERVICE_UNAVAILABLE,
                PracticeConstants.PRACTICE_DETERMINISTIC_SCORING_UNAVAILABLE,
                PracticeConstants.PRACTICE_DETERMINISTIC_SCORING_UNAVAILABLE_MESSAGE);
    }

    private void dispatchPracticeSpeechScoring(PracticeSession session, PracticeSessionItem item) {
        if (speechScoringDispatcher == null || jsonMapper == null
                || !PracticeConstants.PRACTICE_RECORDING_RENDERER_KEYS.contains(item.getRendererKey())
                || item.getSavedPayload() == null) {
            return;
        }
        try {
            var payload = jsonMapper.readTree(item.getSavedPayload());
            String mediaPublicId = payload.path("mediaPublicId").asText(null);
            if (mediaPublicId == null || mediaPublicId.isBlank()) {
                return;
            }
            speechScoringDispatcher.dispatchAfterCommit(item.getPublicId(), session.getTenantId());
        } catch (RuntimeException ignored) {
            // The answer is already durable; malformed optional media metadata does
            // not make a valid practice response fail.
        }
    }

    private void applyFormResult(PracticeSessionItem item, PracticeFormEvaluation result) {
        item.setFormWordCount(result.wordCount());
        item.setFormStatus(result.status());
        item.setFormEvaluatorVersion(result.evaluatorVersion());
        item.setFormResultHash(result.resultHash());
        item.setFormEvidenceIdentityHash(result.evidenceIdentityHash());
    }

    private Set<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void pinQuestionContent(PracticeSessionItem item, String taskCode) {
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
        if (rubricBindingService != null) {
            rubricBindingService.pin(item, taskCode);
        }
        try {
            item.setOptionsJson(jsonMapper.writeValueAsString(content.options()));
            item.setObjectiveOptionsJson(jsonMapper.writeValueAsString(evaluationContent.options()));
        } catch (RuntimeException ex) {
            throw new PracticeSessionException(HttpStatus.SERVICE_UNAVAILABLE,
                    PracticeConstants.PRACTICE_CONTENT_NOT_READY,
                    PracticeConstants.PRACTICE_CONTENT_NOT_READY_MESSAGE);
        }
    }

    private record PracticeEvaluationOption(String text, boolean correct, int orderIndex, Integer blankIndex,
            Integer correctGapIndex) {
    }
}
