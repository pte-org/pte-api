package com.pte.practice.internal.dto.response;

import com.pte.practice.internal.domain.enums.PracticeScoringStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Student-safe scoring outcome of one answered item. It carries the score the exam scorers produced and
 * never the reference or correct answer. {@code retryable} is true only for a FAILED item that may still be re-submitted.
 * {@code subScoreMax} (criterion -> its maximum) is present only when {@code subScores} are Pearson trait scores of a
 * rubric version (shown as achieved / max); it is null for older results, whose sub-scores are raw 0-100 numbers.
 */
public record PracticeItemResultResponse(
        UUID itemPublicId,
        String taskCode,
        PracticeScoringStatus state,
        Integer score,
        String scoreStatus,
        Map<String, Integer> subScores,
        String feedback,
        String transcript,
        String failureCode,
        boolean retryable,
        Instant scoredAt,
        Map<String, Integer> subScoreMax) {

    public PracticeItemResultResponse {
        subScores = subScores == null ? Map.of() : Map.copyOf(subScores);
        subScoreMax = subScoreMax == null ? null : Map.copyOf(subScoreMax);
    }
}
