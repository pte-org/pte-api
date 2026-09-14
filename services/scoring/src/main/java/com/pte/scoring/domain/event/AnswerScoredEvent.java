package com.pte.scoring.domain.event;

import java.util.UUID;

/**
 * Payload for the {@code AnswerScored} outbox event. {@code rawScore} is the
 * scorer's shared 0-100 percentage scale, including binary objective results
 * represented as 0 or 100. Phase 8 owns the 10-90 conversion.
 */
public record AnswerScoredEvent(UUID attemptPublicId, UUID answerPublicId, UUID tenantId, int rawScore) {
}
