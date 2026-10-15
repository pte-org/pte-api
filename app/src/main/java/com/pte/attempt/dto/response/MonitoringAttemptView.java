package com.pte.attempt.dto.response;

import com.pte.attempt.domain.enums.AttemptStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Proctor-scoped snapshot of a student's latest exam attempt on an exam session.
 */
public record MonitoringAttemptView(
        UUID attemptPublicId,
        UUID studentPublicId,
        Integer attemptNumber,
        AttemptStatus status,
        Instant startedAt,
        Instant submittedAt,
        Instant lastSeenAt,
        int attemptCount
) {
}
