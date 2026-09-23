package com.pte.scoring.internal.vendor;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.domain.enums.AiProviderCategory;
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
public record AiScoreResult(int rawScore, Map<String, Integer> subScores, String feedback,
        AiProviderCategory providerCategory, String provider, String model, String providerVersion) {

    public AiScoreResult {
        if (rawScore < 0 || rawScore > 100) {
            throw new IllegalArgumentException(ScoringConstants.RAW_SCORE_RANGE_INVALID);
        }
        if (providerCategory == null || provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("AI score provenance must identify its provider category and provider");
        }
        requireLength(provider, 64, "AI provider");
        if (model != null) {
            requireLength(model, 160, "AI model");
        }
        if (providerVersion != null) {
            requireLength(providerVersion, 100, "AI provider version");
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

    private static void requireLength(String value, int maxLength, String field) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds its maximum length of " + maxLength);
        }
    }
}
