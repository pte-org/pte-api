package com.pte.scoring.vendor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A vendor's scoring result. {@code rawScore} is 0–100 (same scale as
 * {@link com.pte.scoring.service.ObjectiveScoringService} — required so
 * reporting's aggregation can average objective and AI scores uniformly).
 * {@code subScores} (skill name → 0–100) is carried for forward-compatibility
 * with finer-grained enabling-skill reporting — NOT persisted or consumed
 * anywhere yet in Milestone 1 (documented scope cut, phase-09).
 */
public record AiScoreResult(int rawScore, Map<String, Integer> subScores, String feedback) {

    public AiScoreResult {
        if (rawScore < 0 || rawScore > 100) {
            throw new IllegalArgumentException("rawScore must be between 0 and 100");
        }
        Map<String, Integer> safeSubScores = new LinkedHashMap<>();
        if (subScores != null) {
            subScores.forEach((name, score) -> {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("subScore names must not be blank");
                }
                if (score == null || score < 0 || score > 100) {
                    throw new IllegalArgumentException("subScores must be between 0 and 100");
                }
                safeSubScores.put(name, score);
            });
        }
        subScores = Map.copyOf(safeSubScores);
        feedback = Objects.requireNonNullElse(feedback, "");
    }
}
