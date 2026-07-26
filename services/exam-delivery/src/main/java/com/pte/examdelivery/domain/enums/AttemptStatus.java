package com.pte.examdelivery.domain.enums;

/**
 * Attempt lifecycle. SCORING/SCORED/PUBLISHED are driven by events consumed
 * from scoring/scheduling starting Phase 6/7 — this phase only reaches
 * SUBMITTED (host-gated scoring model, ADR-002: student never sees a score
 * until host publishes).
 */
public enum AttemptStatus {
    CREATED,
    IN_PROGRESS,
    SUBMITTED,
    SCORING,
    SCORED,
    PUBLISHED
}
