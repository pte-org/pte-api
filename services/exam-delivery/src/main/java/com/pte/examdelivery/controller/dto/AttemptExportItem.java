package com.pte.examdelivery.controller.dto;

import java.time.Instant;
import java.util.UUID;

/** Reporting read-model rebuild export shape (rabbitmq-outbox-migration Phase 9) — mirrors {@code AttemptSubmittedEvent}. */
public record AttemptExportItem(
        UUID attemptPublicId,
        UUID sessionPublicId,
        UUID studentPublicId,
        UUID tenantId,
        Instant updatedAt) {
}
