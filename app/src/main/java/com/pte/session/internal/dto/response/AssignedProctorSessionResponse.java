package com.pte.session.internal.dto.response;

import com.pte.session.domain.enums.SessionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Proctor assigned exam session response for GET /api/v1/proctor-assignments/mine.
 */
public record AssignedProctorSessionResponse(
        UUID assignmentPublicId,
        UUID examSessionPublicId,
        String name,
        String sessionCode,
        SessionStatus status,
        Instant opensAt,
        Instant closesAt,
        String assignmentRole
) {
}
