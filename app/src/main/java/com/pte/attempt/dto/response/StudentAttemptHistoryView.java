package com.pte.attempt.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Bounded, tenant-scoped attempt row for the host student workspace. */
public record StudentAttemptHistoryView(
        UUID attemptPublicId,
        UUID sessionPublicId,
        UUID studentPublicId,
        UUID tenantId,
        int attemptNumber,
        String status,
        Instant createdAt,
        Instant startedAt,
        Instant submittedAt) {
}
