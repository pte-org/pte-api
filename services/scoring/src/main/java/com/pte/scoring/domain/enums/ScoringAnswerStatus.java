package com.pte.scoring.domain.enums;

/**
 * PENDING = ingested, not yet gradeable-or-graded. AI_SCORING = enqueued to
 * the vendor work queue (Phase 9), in flight. SCORING_FAILED = exhausted
 * retries, dead-lettered (host-visible, not silent). SCORED = final — every
 * AI-scorable task type (including Write Essay) now finalizes straight to
 * SCORED with no host approval gate (quang-host-answer-review Phase 5 removed
 * the prior AI_SCORED_PENDING_REVIEW hold-for-approval state; a host's own
 * independent score, if any, lives separately on {@code
 * ScoringAnswer.teacherScore} and never blocks this status). There is
 * deliberately no status that fakes completion for a task type with no
 * scorer at all — those simply stay PENDING (Phase 7 honest completion
 * semantics, unchanged).
 */
public enum ScoringAnswerStatus {
    PENDING,
    AI_SCORING,
    SCORING_FAILED,
    SCORED
}
