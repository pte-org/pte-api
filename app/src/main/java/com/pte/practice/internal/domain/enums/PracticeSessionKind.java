package com.pte.practice.internal.domain.enums;

/**
 * What a practice session covers. TASK is one task type (the original behaviour and the reading of any session
 * created before V134), SKILL spans every task type of one skill, MOCK_TEST is a fixed full test.
 */
public enum PracticeSessionKind {
    TASK,
    SKILL,
    MOCK_TEST
}
