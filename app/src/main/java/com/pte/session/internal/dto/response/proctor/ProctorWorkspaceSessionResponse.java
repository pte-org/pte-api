package com.pte.session.internal.dto.response.proctor;

import com.pte.session.domain.enums.SessionStatus;

import java.time.Instant;
import java.util.UUID;

public record ProctorWorkspaceSessionResponse(
        UUID assignmentPublicId,
        UUID examSessionPublicId,
        String name,
        String sessionCode,
        SessionStatus status,
        Instant opensAt,
        Instant closesAt,
        String assignmentRole,
        boolean canMonitor,
        String monitoringBlockingReason
) {
}
