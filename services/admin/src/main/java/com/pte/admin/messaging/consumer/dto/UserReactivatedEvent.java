package com.pte.admin.messaging.consumer.dto;

import java.util.UUID;

/** Admin-local wire DTO for IAM's UserReactivated event. */
public record UserReactivatedEvent(UUID userPublicId, UUID tenantId) {
}
