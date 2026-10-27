package com.pte.scoring.internal.service.ai;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.dto.response.AiScoreDetailResponse;
import com.pte.scoring.internal.repository.AiDetailFlags;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the host/examiner view of an answer's AI score. Callers hand over an
 * evaluation they have already authorised, so a client-supplied id never reaches the detail lookup.
 */
@Service
public class AiScoreDetailQueryService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SCORING = "SCORING";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SCORED = "SCORED";

    private static final TypeReference<Map<String, Integer>> CRITERIA = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> DETAIL = new TypeReference<>() { };

    private final AiEvaluationRepository repository;
    private final JsonMapper json;

    public AiScoreDetailQueryService(AiEvaluationRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    /** Null when the answer has no AI side at all (an objective answer, or one not yet queued). */
    @Transactional(readOnly = true)
    public AiScoreDetailResponse find(AiEvaluation answer) {
        return toResponse(answer);
    }

    @Transactional(readOnly = true)
    public Map<UUID, AiScoreDetailResponse> findAll(UUID tenantId, Collection<AiEvaluation> answers) {
        Map<UUID, AiScoreDetailResponse> result = new LinkedHashMap<>();
        for (AiEvaluation answer : answers) {
            AiScoreDetailResponse response = toResponse(answer);
            if (response != null) {
                result.put(answer.getAnswerPublicId(), response);
            }
        }
        return result;
    }

    /** Only the low-confidence flag and failure reason, for rows that never open the full detail. */
    @Transactional(readOnly = true)
    public Map<UUID, AiDetailFlags> findFlags(UUID tenantId, List<UUID> answerPublicIds) {
        if (answerPublicIds.isEmpty()) {
            return Map.of();
        }
        return repository.findFlags(tenantId, answerPublicIds).stream()
                .collect(Collectors.toMap(AiDetailFlags::answerPublicId, Function.identity()));
    }

    private AiScoreDetailResponse toResponse(AiEvaluation answer) {
        boolean hasOutcome = answer.hasAiOutcome();
        String status = statusOf(answer.getStatus(), hasOutcome);
        if (status == null) {
            return null;
        }
        Integer score = STATUS_SCORED.equals(status) ? answer.getRawScore() : null;
        String failureReason = STATUS_FAILED.equals(status) && answer.getFailureReason() != null
                ? answer.getFailureReason().name() : null;
        if (!hasOutcome) {
            return new AiScoreDetailResponse(status, score, Map.of(), null, null, Map.of(), false, failureReason);
        }
        return new AiScoreDetailResponse(status, score, read(answer.getCriteriaScoresJson(), CRITERIA),
                answer.getFeedback(), answer.getTranscript(), read(answer.getDetailJson(), DETAIL),
                answer.isFallback(), failureReason);
    }

    /** SCORED with no AI outcome is an objective answer: it has no AI side to show. */
    private String statusOf(ScoringAnswerStatus status, boolean hasRow) {
        return switch (status) {
            case AI_SCORING -> STATUS_SCORING;
            case SCORING_FAILED -> STATUS_FAILED;
            case SCORED -> hasRow ? STATUS_SCORED : null;
            case PENDING -> hasRow ? STATUS_PENDING : null;
        };
    }

    /** Unreadable stored JSON must not take the review screen down; the other fields still show. */
    private <T> Map<String, T> read(String stored, TypeReference<Map<String, T>> type) {
        if (stored == null || stored.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(stored, type);
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }
}
