package com.pte.scoring.internal.vendor;

import com.pte.scoring.internal.constant.ScoringConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A vendor's scoring result. {@code rawScore} is 0–100 (same scale as {@link
 * com.pte.scoring.internal.service.ObjectiveScoringService} — required so
 * reporting's aggregation can average objective and AI scores uniformly).
 * {@code subScores} (skill name → 0–100) is carried for forward-compatibility
 * with finer-grained enabling-skill reporting — NOT persisted or consumed
 * anywhere yet (documented scope cut).
 */
public record AiScoreResult(int rawScore, Map<String, Integer> subScores, String feedback) {

    public AiScoreResult {
        if (rawScore < 0 || rawScore > 100) {
            throw new IllegalArgumentException(ScoringConstants.RAW_SCORE_RANGE_INVALID);
        }
        Map<String, Integer> safeSubScores = new LinkedHashMap<>();
        if (subScores != null) {
            subScores.forEach((name, score) -> {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException(ScoringConstants.SUBSCORE_NAME_BLANK);
                }
                if (score == null || score < 0 || score > 100) {
                    throw new IllegalArgumentException(ScoringConstants.SUBSCORES_RANGE_INVALID);
                }
                safeSubScores.put(name, score);
            });
        }
        subScores = Map.copyOf(safeSubScores);
        feedback = Objects.requireNonNullElse(feedback, "");
    }
}
