package com.pte.proctor.domain.enums;

/** Attempt-affecting commands proctor can issue; travels to exam-delivery as a {@code ProctorCommand} event. */
public enum ProctorCommandType {
    FORCE_SUBMIT,
    EXTEND_TIME
}
