package com.pte.scoring.domain.enums;

/**
 * PENDING = ingested from {@code AnswerSubmitted}, not yet gradeable-or-graded.
 * SCORED = a rule-based (or, from Phase 9, AI) score has been computed.
 * There is deliberately no "SKIPPED" status — an unsupported task type just
 * stays PENDING until a scorer for it exists (honest completion semantics,
 * phase-07 design constraint).
 */
public enum ScoringAnswerStatus {
    PENDING,
    SCORED
}
