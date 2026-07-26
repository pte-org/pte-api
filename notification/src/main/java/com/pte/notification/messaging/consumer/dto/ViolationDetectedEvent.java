package com.pte.notification.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ViolationDetectedEvent(UUID attemptPublicId, UUID sessionPublicId, String violationType, String detail,
                                      String detectedAt, UUID tenantId) {
}
