package com.pte.scoring.dto.response;

/** One SCORED answer's contribution to reporting's skill aggregation (Phase 10) — same 0-100 scale as {@code ObjectiveScoringService}/AI scoring. */
public record ScoredAnswerView(String taskType, int rawScore) {
}
