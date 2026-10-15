package com.pte.scoring.domain.enums;

/**
 * Status of AI reference score projection for an examiner.
 * AVAILABLE = real, valid 0-100 AI score with complete provenance.
 * PENDING = AI scoring is in flight or queued.
 * FAILED = AI scoring failed / dead-lettered.
 * UNAVAILABLE = item has no AI scorer, stub score, or invalid raw score.
 */
public enum ExaminerAiAvailability {
    AVAILABLE,
    PENDING,
    FAILED,
    UNAVAILABLE
}
