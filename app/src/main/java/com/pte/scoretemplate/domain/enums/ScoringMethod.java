package com.pte.scoretemplate.domain.enums;

/**
 * How a task type's answers get a rawScore. Replaces the old hardcoded
 * the old hardcoded AI/objective task-type catalogs in the scoring module —
 * {@code scoring} now looks this up per task type from the snapshot's
 * pinned {@code ScoreTemplate} instead of two separate hardcoded sets.
 */
public enum ScoringMethod {
    AI_SPEECH,
    AI_TEXT,
    OBJECTIVE,
    UNSCORED
}
