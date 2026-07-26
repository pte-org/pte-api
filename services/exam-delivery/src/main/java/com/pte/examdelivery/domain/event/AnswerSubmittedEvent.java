package com.pte.examdelivery.domain.event;

import java.util.UUID;

/**
 * Consumed by scoring/reporting (event backbone since Phase 6). Does NOT
 * trigger scoring by itself — scoring only acts on the host's
 * {@code ScoringRequested} command (ADR-002 host-gated model).
 *
 * <p>Event-carries-state (Phase 7): includes everything an objective-scoring
 * consumer needs to grade WITHOUT calling back to exam-delivery —
 * {@code taskType}, the student's {@code payload}, and the pinned item's
 * {@code correctAnswerText}/{@code optionsJson}. exam-delivery's own DB
 * copy of this data is unaffected; this is a read-only projection into the event.
 */
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
