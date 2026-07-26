package com.pte.scoring.domain.event;

import java.util.UUID;

/** Payload for the {@code AnswerScored} outbox event. {@code rawScore} is unscaled (0/1 for binary objective grading) — Phase 8 owns the 10–90 conversion. */
public record AnswerScoredEvent(UUID attemptPublicId, UUID answerPublicId, UUID tenantId, int rawScore) {
}
