package com.pte.scoring.domain.enums;

/**
 * PENDING = ingested, not yet gradeable-or-graded. AI_SCORING = enqueued to
 * the vendor work queue (Phase 9), in flight. AI_SCORED_PENDING_REVIEW = the
 * (stub/real) AI pass finished for one of the 7 human-review-required task
 * types (Write Essay in Milestone 1) — held until a host approves.
 * SCORING_FAILED = exhausted retries, dead-lettered (host-visible, not silent).
 * SCORED = final. There is deliberately no status that fakes completion for a
 * task type with no scorer at all — those simply stay PENDING (Phase 7 honest
 * completion semantics, unchanged).
 */
public enum ScoringAnswerStatus {
    PENDING,
    AI_SCORING,
    AI_SCORED_PENDING_REVIEW,
    SCORING_FAILED,
    SCORED
}
