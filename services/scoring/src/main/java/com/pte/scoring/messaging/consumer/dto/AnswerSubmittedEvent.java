package com.pte.scoring.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** scoring's own view of exam-delivery's {@code AnswerSubmitted} payload (§3: no shared DTOs across services). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnswerSubmittedEvent(
        UUID attemptPublicId,
        UUID answerPublicId,
        UUID pinnedItemPublicId,
        UUID sessionPublicId,
        UUID tenantId,
        boolean expired,
        String taskType,
        String payload,
        String correctAnswerText,
        String optionsJson) {
}
