package com.pte.attempt.domain.enums;

/**
 * Attempt lifecycle. SCORING/SCORED/PUBLISHED are driven by application
 * events consumed from scoring/session starting Phase 08/10 — this phase
 * only reaches SUBMITTED (host-gated scoring model: student never sees a
 * score until host publishes).
 */
public enum AttemptStatus {
    CREATED,
    IN_PROGRESS,
    SUBMITTED,
    SCORING,
    SCORED,
    PUBLISHED
}
