package com.pte.examdelivery.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * exam-delivery's own local view of proctor's outbox payload — no shared DTO across services (ADR-002).
 * {@code ignoreUnknown} also covers a stray {@code extraSeconds} from an in-flight EXTEND_TIME message
 * produced by a not-yet-upgraded proctor instance during a rolling deploy (client-side-exam-timer
 * Phase 5 removed the EXTEND_TIME command and this field entirely — an accepted capability loss, see
 * that plan's Phase 5 Session Notes for why).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProctorCommandEvent(UUID attemptPublicId, UUID sessionPublicId, String commandType, UUID tenantId) {
}
