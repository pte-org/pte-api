package com.pte.scoring;

import java.time.Instant;
import java.util.Map;

/**
 * Provider result for a practice response. The record is owned by scoring so
 * Practice can consume Azure or another configured provider without importing
 * vendor implementations or creating an exam attempt.
 */
public record PracticeAiEvaluation(int rawScore, Map<String, Integer> subScores,
        String scoreStatus, String provider, String model, String providerVersion, String transcript,
        String detailJson, Instant evaluatedAt, String resultHash) {

    public PracticeAiEvaluation {
        if (rawScore < 0 || rawScore > 100) {
            throw new IllegalArgumentException("Practice AI score must be between 0 and 100");
        }
        subScores = subScores == null ? Map.of() : Map.copyOf(subScores);
    }
}
