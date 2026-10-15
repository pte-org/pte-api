package com.pte.attempt.dto.response;

import com.pte.attempt.domain.enums.AttemptStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary view for historical attempt items belonging to an enrolled student.
 */
public record MonitoringAttemptSummaryView(
        UUID attemptPublicId,
        UUID studentPublicId,
        int attemptNumber,
        AttemptStatus status,
        Instant startedAt,
        Instant submittedAt,
        Instant lastSeenAt
) {
}
