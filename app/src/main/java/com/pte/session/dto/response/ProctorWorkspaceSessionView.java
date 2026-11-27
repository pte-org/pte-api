package com.pte.session.dto.response;

import com.pte.session.domain.enums.SessionStatus;

import java.time.Instant;
import java.util.UUID;

public record ProctorWorkspaceSessionView(
        UUID assignmentPublicId,
        UUID examSessionPublicId,
        String name,
        String sessionCode,
        SessionStatus status,
        Instant opensAt,
        Instant closesAt,
        boolean canMonitor,
        String monitoringBlockingReason
) {
}
