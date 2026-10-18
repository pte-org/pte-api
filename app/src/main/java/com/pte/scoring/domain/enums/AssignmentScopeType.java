package com.pte.scoring.domain.enums;

public enum AssignmentScopeType {
    CLASS,
    PROGRAM,
    /** A single student, referenced by the student's public id. */
    STUDENT,
    /** Every submitted attempt of the exam session, referenced by the session's public id. Random mode only. */
    SESSION
}
