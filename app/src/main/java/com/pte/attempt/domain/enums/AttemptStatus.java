package com.pte.attempt.domain.enums;

/**
 * Attempt lifecycle — stops at SUBMITTED. Scoring progress and publish
 * visibility are each a different module's own bounded fact, not a
 * projection of this status: {@code scoring.ScoringAnswer.status}
 * (PENDING/AI_SCORING/SCORING_FAILED/SCORED, per answer) and {@code
 * reporting.AttemptReport.published} (the visibility gate) — neither is
 * mirrored back onto this enum, so there is exactly one owner for each fact.
 */
public enum AttemptStatus {
    CREATED,
    IN_PROGRESS,
    SUBMITTED
}
