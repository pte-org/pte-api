package com.pte.reporting.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** reporting's own view of scoring's {@code AnswerScored} payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnswerScoredEvent(UUID attemptPublicId, UUID answerPublicId, UUID tenantId, int rawScore) {
}
