package com.pte.session.dto.event;

import java.time.Instant;
import java.util.UUID;

/** Published after a session cancellation commits; recipients are resolved from staff assignments. */
public record SessionCancelledEvent(UUID sessionPublicId, UUID tenantId, String sessionName,
        Instant cancelledAt) {
}
