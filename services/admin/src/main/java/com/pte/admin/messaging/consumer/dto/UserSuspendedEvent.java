package com.pte.admin.messaging.consumer.dto;

import java.util.UUID;

/** Admin-local wire DTO for IAM's UserSuspended event. */
public record UserSuspendedEvent(UUID userPublicId, UUID tenantId) {
}
