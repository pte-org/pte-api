package com.aptis.common.interfaces;

/**
 * Lets `proctor`'s STOMP subscription authorization check "does this student have an
 * attempt for this exam" without depending on `examdelivery` directly (examdelivery already
 * depends on proctor for status push/audit — a reverse dependency would cycle). Implemented
 * by examdelivery, which owns ExamAttempt.
 */
public interface StudentAttemptLookup {
    boolean hasAttemptForExam(Long studentId, Long examId);
}
