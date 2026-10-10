package com.pte.enrollment.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Compact activity row; report scores are present only for immutable snapshots. */
public record StudentAttemptHistoryResponse(
        UUID attemptPublicId,
        UUID sessionPublicId,
        String sessionName,
        String sessionCode,
        String sessionStatus,
        int attemptNumber,
        String status,
        Instant createdAt,
        Instant startedAt,
        Instant submittedAt,
        String reportState,
        boolean reportAvailable,
        Integer overallScore,
        boolean overallSufficientData) {
}
