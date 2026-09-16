package com.pte.scoring.domain.enums;

/**
 * PENDING = ingested, not yet gradeable-or-graded. AI_SCORING = enqueued to
 * the vendor work queue, in flight. SCORING_FAILED = exhausted retries,
 * dead-lettered (host-visible, not silent). SCORED = final — every
 * AI-scorable task type (including Write Essay) finalizes straight to SCORED
 * with no host approval gate; a host's own independent score, if any, lives
 * separately on {@code ScoringAnswer.teacherScore} and never blocks this
 * status. There is deliberately no status that fakes completion for a task
 * type with no scorer at all — those simply stay PENDING (honest completion
 * semantics).
 */
public enum ScoringAnswerStatus {
    PENDING,
    AI_SCORING,
    SCORING_FAILED,
    SCORED
}
