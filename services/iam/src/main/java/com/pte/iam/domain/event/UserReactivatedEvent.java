package com.pte.iam.domain.event;

import java.util.UUID;

/** Payload for the {@code UserReactivated} outbox event. */
public record UserReactivatedEvent(UUID userPublicId, UUID tenantId) {
}
