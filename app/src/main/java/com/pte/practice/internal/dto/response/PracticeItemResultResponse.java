package com.pte.practice.internal.dto.response;

import com.pte.practice.internal.domain.enums.PracticeScoringStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Student-safe scoring outcome of one answered item. It carries the score the exam scorers produced and
 * never the reference or correct answer. {@code retryable} is true only for a FAILED item that may still be re-submitted.
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
        Instant scoredAt) {

    public PracticeItemResultResponse {
        subScores = subScores == null ? Map.of() : Map.copyOf(subScores);
    }
}
