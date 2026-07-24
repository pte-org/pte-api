package com.pte.iam.domain.event;

import java.util.List;
import java.util.UUID;

/** Payload for the {@code UserCreated} outbox event (consumed by notification, etc.). */
public record UserCreatedEvent(UUID userPublicId, String email, UUID tenantId, List<String> roles) {
}
