package com.pte.scoring.domain.enums;

/** Safe response-content variants exposed by the blind Examiner API. */
public enum ExaminerAnswerContentKind {
    AUDIO,
    TEXT,
    SELECTION,
    POSITIONAL_SELECTION,
    WORD_INDICES,
    UNRECOGNIZED
}
