package com.pte.notification.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StudentEnrolledEvent(UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
}
