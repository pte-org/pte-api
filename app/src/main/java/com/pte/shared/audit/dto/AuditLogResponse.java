package com.pte.shared.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID publicId,
        UUID actorUserId,
        String aggregateType,
        String aggregateId,
        String action,
        String summary,
        Instant createdAt) {
}
