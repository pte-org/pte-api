package com.pte.proctoring.internal.dto.response;

import com.pte.attempt.domain.enums.AttemptStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Proctor-scoped projection of one enrolled student on the monitoring roster.
 */
public record MonitoringStudentResponse(
        UUID studentPublicId,
        String displayName,
        String email,
        UUID attemptPublicId,
        Integer attemptNumber,
        AttemptStatus status,
        Instant startedAt,
        Instant submittedAt,
        Instant lastSeenAt,
        int attemptCount,
        long manualViolationCount
) {
}
