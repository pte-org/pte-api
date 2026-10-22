package com.pte.practice.internal.service;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeExplanation;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeExplanationStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.dto.response.PracticeExplanationResponse;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.repository.PracticeExplanationRepository;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.scoring.PracticeExplanationGenerator;
import com.pte.scoring.PracticeAiProperties;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Owns immutable, language-specific explanation snapshots for submitted items. */
@Service
public class PracticeExplanationService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeSessionItemRepository itemRepository;
    private final PracticeEntitlementService entitlementService;
    private final PracticeExplanationRepository explanationRepository;
    private final PracticeExplanationJobService jobService;
    private final ObjectProvider<PracticeExplanationGenerator> generatorProvider;
    private final ObjectProvider<PracticeAiProperties> aiPropertiesProvider;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public PracticeExplanationService(PracticeSessionRepository sessionRepository,
            PracticeSessionItemRepository itemRepository, PracticeExplanationRepository explanationRepository,
            PracticeEntitlementService entitlementService, PracticeExplanationJobService jobService,
            ObjectProvider<PracticeExplanationGenerator> generatorProvider,
            ObjectProvider<PracticeAiProperties> aiPropertiesProvider, JsonMapper jsonMapper, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
        this.entitlementService = entitlementService;
        this.jobService = jobService;
        this.explanationRepository = explanationRepository;
        this.generatorProvider = generatorProvider;
        this.aiPropertiesProvider = aiPropertiesProvider;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public PracticeExplanationResponse request(UUID sessionPublicId, UUID itemPublicId, String language,
            CurrentUser caller) {
        return request(sessionPublicId, itemPublicId, language, null, caller);
    }

    @Transactional
    public PracticeExplanationResponse request(UUID sessionPublicId, UUID itemPublicId, String language,
            String idempotencyKey, CurrentUser caller) {
        String normalizedLanguage = normalizeLanguage(language);
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        PracticeSession session = findSession(sessionPublicId, caller.userId());
        entitlementService.assertUnlocked(caller, session.getTenantId());
        PracticeSessionItem item = findItemForUpdate(session, itemPublicId);
        if (item.getStatus() != PracticeSessionItemStatus.ANSWERED
                || item.getSavedPayload() == null || item.getSavedPayload().isBlank()) {
            throw new com.pte.practice.internal.exception.PracticeSessionException(HttpStatus.CONFLICT,
                    PracticeConstants.PRACTICE_EXPLANATION_NOT_READY,
                    PracticeConstants.PRACTICE_EXPLANATION_NOT_READY_MESSAGE);
        }

        String rubricVersion = rubricVersion();
        String submissionHash = submissionHash(item, normalizedLanguage, rubricVersion);
        var existing = explanationRepository.findByPracticeSessionItemIdAndLanguageAndGenerationSequenceAndDeletedFalse(
                item.getId(), normalizedLanguage, 0);
        if (existing.isPresent() && existing.get().getSubmissionHash().equals(submissionHash)
                && existing.get().getStatus() != PracticeExplanationStatus.FAILED
                && existing.get().getStatus() != PracticeExplanationStatus.CANCELLED
                && existing.get().getStatus() != PracticeExplanationStatus.UNAVAILABLE) {
            return toResponse(existing.get(), item);
        }

        // A changed rubric/model never overwrites the immutable explanation slot. A later
        // regeneration will use a new generation sequence once that explicit workflow exists.
        if (existing.isPresent() && !existing.get().getSubmissionHash().equals(submissionHash)) {
            return toResponse(existing.get(), item);
        }

        PracticeExplanation explanation = existing.orElseGet(PracticeExplanation::new);
        explanation.setPracticeSessionItemId(item.getId());
        explanation.setStudentPublicId(caller.userId());
        explanation.setLanguage(normalizedLanguage);
        explanation.setGenerationSequence(0);
        explanation.setSubmissionHash(submissionHash);
        explanation.setRubricVersion(rubricVersion);
        PracticeExplanationGenerator generator = generatorProvider.getIfAvailable();
        if (generator == null) {
            explanation.setStatus(PracticeExplanationStatus.UNAVAILABLE);
            explanation.setCanRetry(false);
            explanation.setAttemptCount(0);
            explanation.setFailureCode(null);
            explanation.setSummary(null);
            explanation.setGeneratedAt(null);
            return toResponse(save(explanation), item);
        }
        PracticeAiProperties properties = aiPropertiesProvider.getIfAvailable();
        String requestedModel = properties == null ? null : properties.getModel();
        return toResponse(jobService.enqueue(explanation, normalizedIdempotencyKey, requestedModel), item);
    }

    @Transactional(readOnly = true)
    public PracticeExplanationResponse get(UUID sessionPublicId, UUID itemPublicId, String language,
            CurrentUser caller) {
        String normalizedLanguage = normalizeLanguage(language);
        PracticeSession session = findSession(sessionPublicId, caller.userId());
        PracticeSessionItem item = findItem(session, itemPublicId);
        return explanationRepository.findByPracticeSessionItemIdAndLanguageAndGenerationSequenceAndDeletedFalse(
                        item.getId(), normalizedLanguage, 0)
                .map(explanation -> toResponse(explanation, item))
                .orElseGet(() -> unavailableResponse(item, normalizedLanguage));
    }

    private PracticeExplanation save(PracticeExplanation explanation) {
        return explanationRepository.saveAndFlush(explanation);
    }

    private PracticeSession findSession(UUID publicId, UUID studentPublicId) {
        return sessionRepository.findByPublicIdAndStudentPublicIdAndDeletedFalse(publicId, studentPublicId)
                .orElseThrow(PracticeSessionNotFoundException::new);
    }

    private PracticeSessionItem findItem(PracticeSession session, UUID itemPublicId) {
        return itemRepository.findByPublicIdAndPracticeSessionIdAndDeletedFalse(itemPublicId, session.getId())
                .orElseThrow(PracticeSessionNotFoundException::new);
    }

    private PracticeSessionItem findItemForUpdate(PracticeSession session, UUID itemPublicId) {
        return itemRepository.findWithLockByPublicIdAndPracticeSessionId(itemPublicId, session.getId())
                .orElseThrow(PracticeSessionNotFoundException::new);
    }

    private PracticeExplanationResponse unavailableResponse(PracticeSessionItem item, String language) {
        return new PracticeExplanationResponse(null, item.getPublicId(), language,
                PracticeExplanationStatus.UNAVAILABLE.name(), null, List.of(), List.of(), List.of(),
                rubricVersion(), null, null, null, null, null, false, 0, null);
    }

    private PracticeExplanationResponse toResponse(PracticeExplanation explanation, PracticeSessionItem item) {
        return new PracticeExplanationResponse(explanation.getPublicId(), item.getPublicId(),
                explanation.getLanguage(), explanation.getStatus().name(), explanation.getSummary(),
                readList(explanation.getStrengthsJson()), readList(explanation.getImprovementsJson()),
                readRubric(explanation.getRubricFeedbackJson()), explanation.getRubricVersion(),
                explanation.getRequestedModel(), explanation.getActualModelVersion(),
                explanation.getSubmissionHash(), explanation.getGeneratedAt(), explanation.getFailureCode(),
                explanation.isCanRetry(), explanation.getAttemptCount(), explanation.getNextAttemptAt());
    }

    private List<String> readList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return jsonMapper.readValue(value, new TypeReference<List<String>>() { });
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<PracticeExplanationResponse.RubricFeedback> readRubric(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return jsonMapper.readValue(value,
                    new TypeReference<List<PracticeExplanationResponse.RubricFeedback>>() { });
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private String normalizeLanguage(String language) {
        String normalized = language == null ? "vi" : language.trim().toLowerCase();
        if (!normalized.equals("vi") && !normalized.equals("en")) {
            throw new com.pte.practice.internal.exception.PracticeSessionException(HttpStatus.BAD_REQUEST,
                    PracticeConstants.PRACTICE_EXPLANATION_LANGUAGE_INVALID,
                    PracticeConstants.PRACTICE_EXPLANATION_LANGUAGE_INVALID_MESSAGE);
        }
        return normalized;
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return "practice-explanation-" + UUID.randomUUID();
        }
        String normalized = value.trim();
        if (normalized.length() > PracticeConstants.MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new com.pte.practice.internal.exception.PracticeSessionException(HttpStatus.BAD_REQUEST,
                    PracticeConstants.PRACTICE_EXPLANATION_IDEMPOTENCY_KEY_INVALID,
                    PracticeConstants.PRACTICE_EXPLANATION_IDEMPOTENCY_KEY_INVALID_MESSAGE);
        }
        return normalized;
    }

    private String submissionHash(PracticeSessionItem item, String language, String rubricVersion) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = (item.getTaskCode() + "\n" + item.getPromptText() + "\n"
                    + item.getSavedPayload() + "\n" + language + "\n"
                    + rubricVersion).getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(PracticeConstants.PRACTICE_SHA256_UNAVAILABLE, exception);
        }
    }

    private String rubricVersion() {
        PracticeAiProperties properties = aiPropertiesProvider.getIfAvailable();
        if (properties == null || properties.getRubricVersion() == null
                || properties.getRubricVersion().isBlank()) {
            return PracticeConstants.PRACTICE_DEFAULT_RUBRIC_VERSION;
        }
        return properties.getRubricVersion().trim();
    }
}
