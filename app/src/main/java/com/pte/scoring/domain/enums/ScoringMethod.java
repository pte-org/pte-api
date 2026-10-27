package com.pte.scoring.domain.enums;

/**
 * How a task type's rawScore gets produced — read from the exam version item's runtime profile when the answer
 * is opened for scoring. A local enum rather than an import of the item bank's, so {@code scoring} stays
 * independent of its internals; {@code taskType}/{@code section} cross module lines as plain Strings.
 */
public enum ScoringMethod {
    AI_SPEECH,
    AI_TEXT,
    OBJECTIVE,
    UNSCORED;

    public boolean isAiScored() {
        return this == AI_SPEECH || this == AI_TEXT;
    }
}
