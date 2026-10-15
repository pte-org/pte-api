package com.pte.scoring.domain.enums;

/**
 * Kind of human examiner decision recorded for an answer.
 * CONFIRM_AI = examiner confirmed the viewed AI reference score.
 * MANUAL = examiner entered a custom score (0-100).
 * LEGACY_IMPORTED = server/migration-only backfilled decision.
 */
public enum ExaminerDecisionKind {
    CONFIRM_AI,
    MANUAL,
    LEGACY_IMPORTED
}
