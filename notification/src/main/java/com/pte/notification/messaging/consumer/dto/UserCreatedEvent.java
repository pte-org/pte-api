package com.pte.notification.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/** notification's own local view of iam's outbox payload — no shared DTO across services (ADR-002). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserCreatedEvent(UUID userPublicId, String email, UUID tenantId, List<String> roles) {
}
