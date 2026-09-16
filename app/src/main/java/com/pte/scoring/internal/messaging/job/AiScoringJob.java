package com.pte.scoring.internal.messaging.job;

import java.util.UUID;

/**
 * RabbitMQ message payload — one AI scoring job, dispatched after a host's
 * scoring request. {@code scoringMethod} ({@code "AI_SPEECH"}/{@code
 * "AI_TEXT"}) is resolved once at dispatch time from the pinned {@code
 * ScoreTemplate} (spec FR-07) and carried here so {@code AiScoringWorker}
 * never has to re-resolve it (or consult the old hardcoded task-type
 * catalog it used to) on the consuming side.
 */
public record AiScoringJob(
        UUID answerPublicId,
        UUID attemptPublicId,
        UUID sessionPublicId,
        UUID tenantId,
        String taskType,
        String payload,
        String referenceText,
        String scoringMethod) {
}
