package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ExaminerAiAvailability;

import java.time.Instant;

/**
 * Blind-safe AI reference score projection for the assigned examiner.
 * Score is present only when availability is AVAILABLE.
 * PII, provider secrets, and correct answers are excluded.
 */
public record ExaminerAiReferenceResponse(
        ExaminerAiAvailability availability,
        Integer score,
        Instant scoredAt,
        String version) {
}
