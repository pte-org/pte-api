package com.pte.proctoring.internal.dto.response;

import java.util.UUID;

/**
 * Socket-scoped correlated failure response for /user/queue/proctor-errors.
 */
public record ProctorOperationErrorResponse(
        String requestId,
        String operation,
        UUID targetPublicId,
        int status,
        String code
) {
}
