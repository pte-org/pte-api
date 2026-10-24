package com.pte.practice.internal.domain.enums;

/** Server-owned scoring lifecycle of one answered practice item. SCORED is final. */
public enum PracticeScoringStatus {
    NONE,
    SCORING,
    SCORED,
    FAILED
}
