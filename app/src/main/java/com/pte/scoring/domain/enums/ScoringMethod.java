package com.pte.scoring.domain.enums;

/**
 * How a task type's rawScore gets produced — resolved per-answer from its
 * pinned {@code ScoreTemplate} (spec FR-07), via {@code
 * ScoringMethodResolver}. A local copy of {@code
 * scoretemplate.domain.enums.ScoringMethod}'s 4 values rather than an import
 * of it — {@code scoring} stays independent of {@code scoretemplate}'s
 * internal enums, matching how {@code taskType}/{@code section} already
 * cross module lines as plain Strings elsewhere in this codebase.
 */
public enum ScoringMethod {
    AI_SPEECH,
    AI_TEXT,
    OBJECTIVE,
    UNSCORED
}
