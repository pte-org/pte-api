package com.pte.examdelivery.controller.dto;

import java.time.Instant;
import java.util.UUID;

/** Reporting read-model rebuild export shape (rabbitmq-outbox-migration Phase 9) — mirrors {@code AnswerSubmittedEvent}'s reporting-relevant subset. */
public record AnswerExportItem(
        UUID answerPublicId,
        UUID attemptPublicId,
        UUID tenantId,
        String taskType,
        Instant updatedAt) {
}
