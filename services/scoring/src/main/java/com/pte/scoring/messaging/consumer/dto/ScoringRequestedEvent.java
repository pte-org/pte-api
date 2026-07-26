package com.pte.scoring.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** scoring's own view of scheduling's {@code ScoringRequested} payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoringRequestedEvent(UUID sessionPublicId, UUID tenantId) {
}
