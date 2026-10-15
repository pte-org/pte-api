package com.pte.proctoring.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Socket-scoped correlated outcome response for /user/queue/proctor-results.
 */
public record ProctorOperationResultResponse(
        String requestId,
        String operation,
        UUID targetPublicId,
        String outcome,
        UUID eventPublicId,
        Instant occurredAt
) {
}
