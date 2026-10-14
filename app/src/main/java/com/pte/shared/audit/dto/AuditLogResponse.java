package com.pte.shared.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID publicId,
        UUID actorUserId,
        String aggregateType,
        String aggregateId,
        String resourceName,
        String resourceCode,
        String action,
        String summary,
        Instant createdAt) {

    /** Backward-compatible constructor for dashboard projections that do not expose resource identity. */
    public AuditLogResponse(UUID publicId, UUID actorUserId, String aggregateType, String aggregateId,
            String action, String summary, Instant createdAt) {
        this(publicId, actorUserId, aggregateType, aggregateId, null, null, action, summary, createdAt);
    }
}
