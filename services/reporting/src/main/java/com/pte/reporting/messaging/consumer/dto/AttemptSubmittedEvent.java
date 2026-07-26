package com.pte.reporting.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** reporting's own view of exam-delivery's {@code AttemptSubmitted} payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AttemptSubmittedEvent(UUID attemptPublicId, UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
}
