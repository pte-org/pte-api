package com.pte.iam.domain.event;

import java.util.UUID;

/** Payload for the {@code UserSuspended} outbox event. */
public record UserSuspendedEvent(UUID userPublicId, UUID tenantId) {
}
