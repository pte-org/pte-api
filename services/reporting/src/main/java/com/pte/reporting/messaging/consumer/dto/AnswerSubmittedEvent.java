package com.pte.reporting.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * reporting's own view of exam-delivery's {@code AnswerSubmitted} payload —
 * only what's needed for skill mapping (taskType), not the answer content or
 * correct-answer data (that's scoring's concern). {@code ignoreUnknown}
 * because the real payload carries more fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnswerSubmittedEvent(UUID attemptPublicId, UUID answerPublicId, UUID tenantId, String taskType) {
}
