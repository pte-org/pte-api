package com.pte.practice.internal.domain.enums;

/** Honest read-only state of a practice history row. */
public enum PracticeProgressStatus {
    IN_PROGRESS,
    COMPLETED_PENDING_SCORE,
    COMPLETED_SCORED,
    SCORING_FAILED
}
