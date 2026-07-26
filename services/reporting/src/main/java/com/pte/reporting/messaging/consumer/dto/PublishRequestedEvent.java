package com.pte.reporting.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** reporting's own view of scheduling's {@code PublishRequested} payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PublishRequestedEvent(UUID sessionPublicId, UUID tenantId) {
}
