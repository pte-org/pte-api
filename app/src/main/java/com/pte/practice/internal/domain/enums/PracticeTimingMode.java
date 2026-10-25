package com.pte.practice.internal.domain.enums;

/**
 * How a practice session is paced. TIMED follows the exam rhythm per item (timing pinned from the active Score
 * Template, enforced by the client); UNTIMED has no countdown at all. Sessions created before V133 have no
 * stored mode and read as UNTIMED.
 */
public enum PracticeTimingMode {
    TIMED,
    UNTIMED
}
