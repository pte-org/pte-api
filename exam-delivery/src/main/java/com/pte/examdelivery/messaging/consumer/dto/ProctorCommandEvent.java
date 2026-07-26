package com.pte.examdelivery.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** exam-delivery's own local view of proctor's outbox payload — no shared DTO across services (ADR-002). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProctorCommandEvent(UUID attemptPublicId, UUID sessionPublicId, String commandType,
                                   Integer extraSeconds, UUID tenantId) {
}
