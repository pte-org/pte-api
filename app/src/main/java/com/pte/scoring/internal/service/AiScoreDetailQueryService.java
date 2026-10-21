package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringAnswerAiDetail;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.dto.response.AiScoreDetailResponse;
import com.pte.scoring.internal.repository.AiDetailFlags;
import com.pte.scoring.internal.repository.ScoringAnswerAiDetailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the host/examiner view of an answer's AI score. Callers hand over an
 * answer they have already authorised; the lookup is keyed by that answer's
 * own id and tenant, so a client-supplied id never reaches the detail table.
 */
@Service
public class AiScoreDetailQueryService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SCORING = "SCORING";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SCORED = "SCORED";

    private static final TypeReference<Map<String, Integer>> CRITERIA = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> DETAIL = new TypeReference<>() { };

    private final ScoringAnswerAiDetailRepository repository;
    private final JsonMapper json;

    public AiScoreDetailQueryService(ScoringAnswerAiDetailRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    /** Null when the answer has no AI side at all (an objective answer, or one not yet queued). */
    @Transactional(readOnly = true)
    public AiScoreDetailResponse find(ScoringAnswer answer) {
        return toResponse(answer, repository.findByAnswerPublicIdAndTenantId(
                answer.getAnswerPublicId(), answer.getTenantId()));
    }

    @Transactional(readOnly = true)
    public Map<UUID, AiScoreDetailResponse> findAll(UUID tenantId, Collection<ScoringAnswer> answers) {
        if (answers.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ScoringAnswerAiDetail> rows = repository.findByTenantIdAndAnswerPublicIdIn(tenantId,
                answers.stream().map(ScoringAnswer::getAnswerPublicId).toList()).stream()
                .collect(Collectors.toMap(ScoringAnswerAiDetail::getAnswerPublicId, Function.identity()));
        Map<UUID, AiScoreDetailResponse> result = new LinkedHashMap<>();
        for (ScoringAnswer answer : answers) {
            AiScoreDetailResponse response = toResponse(answer, Optional.ofNullable(rows.get(answer.getAnswerPublicId())));
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

    private AiScoreDetailResponse toResponse(ScoringAnswer answer, Optional<ScoringAnswerAiDetail> row) {
        String status = statusOf(answer.getStatus(), row.isPresent());
        if (status == null) {
            return null;
        }
        Integer score = STATUS_SCORED.equals(status) ? answer.getRawScore() : null;
        String failureReason = STATUS_FAILED.equals(status)
                ? row.map(ScoringAnswerAiDetail::getFailureReason).map(Enum::name).orElse(null) : null;
        if (row.isEmpty()) {
            return new AiScoreDetailResponse(status, score, Map.of(), null, null, Map.of(), false, failureReason);
        }
        ScoringAnswerAiDetail detail = row.get();
        return new AiScoreDetailResponse(status, score, read(detail.getCriteriaScoresJson(), CRITERIA),
                detail.getFeedback(), detail.getTranscript(), read(detail.getDetailJson(), DETAIL),
                detail.isFallback(), failureReason);
    }

    /** SCORED with no AI row is an objective answer: it has no AI side to show. */
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
