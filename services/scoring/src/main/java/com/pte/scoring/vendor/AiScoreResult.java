package com.pte.scoring.vendor;

import java.util.Map;

/**
 * A vendor's scoring result. {@code rawScore} is 0–100 (same scale as
 * {@link com.pte.scoring.service.ObjectiveScoringService} — required so
 * reporting's aggregation can average objective and AI scores uniformly).
 * {@code subScores} (skill name → 0–100) is carried for forward-compatibility
 * with finer-grained enabling-skill reporting — NOT persisted or consumed
 * anywhere yet in Milestone 1 (documented scope cut, phase-09).
 */
public record AiScoreResult(int rawScore, Map<String, Integer> subScores, String feedback) {
}
