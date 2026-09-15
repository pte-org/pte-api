package com.pte.admin.messaging.consumer.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin-local wire DTO for IAM's additive UserCreated event contract. */
public record UserCreatedEvent(UUID userPublicId, String email, UUID tenantId, List<String> roles,
                               String fullName, String studentCode, String phone, String status,
                               Instant createdAt) {
}
