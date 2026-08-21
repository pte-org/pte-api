package com.pte.scoring.controller.dto;

import java.time.Instant;
import java.util.UUID;

/** Reporting read-model rebuild export shape (rabbitmq-outbox-migration Phase 9) — mirrors {@code AnswerScoredEvent}. */
public record AnswerScoredExportItem(
        UUID answerPublicId,
        UUID attemptPublicId,
        UUID tenantId,
        int rawScore,
        Instant updatedAt) {
}
