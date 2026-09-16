package com.pte.scoretemplate.domain.enums;

/**
 * Whether a task type's {@code responseSeconds} is a hard limit enforced by
 * the exam runner (FIXED — all Speaking/Writing/Listening-audio task types)
 * or a recommendation shown to the candidate only (RECOMMENDED — Reading and
 * most Listening task types, per the APEUni V5 table).
 */
public enum TimingMode {
    FIXED,
    RECOMMENDED
}
