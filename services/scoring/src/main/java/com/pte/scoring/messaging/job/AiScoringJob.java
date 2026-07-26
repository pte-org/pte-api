package com.pte.scoring.messaging.job;

import java.util.UUID;

/** RabbitMQ message payload — one AI scoring job, dispatched after ScoringRequested. */
public record AiScoringJob(
        UUID answerPublicId,
        UUID attemptPublicId,
        UUID sessionPublicId,
        UUID tenantId,
        String taskType,
        String payload,
        String referenceText) {
}
