package com.pte.proctoring.domain.enums;

/**
 * Where an {@code ExamIncident} came from. {@link #AUTO} is lockdown evidence the student client reports by
 * itself; {@link #PROCTOR_FLAG} is a violation a proctor flags live; {@link #REPORTED} is a manually reported
 * incident that follows the NEW → CLOSED handling workflow. AUTO and PROCTOR_FLAG rows are security evidence and
 * make up the security audit; only REPORTED rows are listed as incidents.
 */
public enum IncidentOrigin {
    AUTO,
    PROCTOR_FLAG,
    REPORTED
}
