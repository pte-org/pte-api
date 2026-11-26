package com.pte.shared.audit.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One audit row in a list. {@code hasStateChange} tells the client a before/after snapshot exists; the snapshots
 * themselves are only returned by the platform detail endpoint, because they may hold answer keys.
 */
public record AuditLogResponse(
        UUID publicId,
        UUID actorUserId,
        String actorRole,
        String aggregateType,
        String aggregateId,
        String resourceName,
        String resourceCode,
        String action,
        String summary,
        boolean hasStateChange,
        Instant createdAt) {

    /** Backward-compatible constructor for projections without actor role or state information. */
    public AuditLogResponse(UUID publicId, UUID actorUserId, String aggregateType, String aggregateId,
            String resourceName, String resourceCode, String action, String summary, Instant createdAt) {
        this(publicId, actorUserId, null, aggregateType, aggregateId, resourceName, resourceCode, action, summary,
                false, createdAt);
    }

    /** Backward-compatible constructor for dashboard projections that do not expose resource identity. */
    public AuditLogResponse(UUID publicId, UUID actorUserId, String aggregateType, String aggregateId,
            String action, String summary, Instant createdAt) {
        this(publicId, actorUserId, aggregateType, aggregateId, null, null, action, summary, createdAt);
    }
}
