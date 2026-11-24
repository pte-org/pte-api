package com.pte.session.dto.event;

import java.util.UUID;

/**
 * Published inside the transaction that closes a session (by the host or automatically at its close time),
 * so listeners that must settle the session's attempts commit or roll back together with the close.
 */
public record SessionClosedEvent(UUID sessionPublicId, UUID tenantId) {
}
