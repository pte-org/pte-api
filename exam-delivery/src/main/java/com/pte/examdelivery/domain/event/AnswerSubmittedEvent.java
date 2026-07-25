package com.pte.examdelivery.domain.event;

import java.util.UUID;

/**
 * Consumed by scoring/reporting once the event backbone lands (Phase 6). Does
 * NOT trigger scoring by itself — scoring only acts on the host's
 * {@code ScoringRequested} command (ADR-002 host-gated model). This carries
 * enough for reporting to build its read model.
 */
public record AnswerSubmittedEvent(
        UUID attemptPublicId, UUID answerPublicId, UUID pinnedItemPublicId, UUID tenantId, boolean expired) {
}
