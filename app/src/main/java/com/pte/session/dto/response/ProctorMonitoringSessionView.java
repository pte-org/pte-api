package com.pte.session.dto.response;

import com.pte.session.domain.enums.SessionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Proctor-scoped read view of an exam session and the caller's assignment role.
 */
public record ProctorMonitoringSessionView(
        UUID assignmentPublicId,
        UUID examSessionPublicId,
        String name,
        String sessionCode,
        SessionStatus status,
        Instant opensAt,
        Instant closesAt,
        String assignmentRole,
        UUID proctorPublicId
) {
}
