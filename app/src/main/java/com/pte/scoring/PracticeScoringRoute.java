package com.pte.scoring;

/**
 * How the scoring module scores a task type, derived from the same runtime
 * profile table the exam uses. {@code UNSCORED} (Personal Introduction) is a
 * valid task type with no score; {@code UNSUPPORTED} is not a task type at all.
 */
public enum PracticeScoringRoute {
    AI_SPEECH,
    AI_TEXT,
    OBJECTIVE,
    UNSCORED,
    UNSUPPORTED
}
