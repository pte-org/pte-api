package com.pte.scoring.internal.messaging.job;

import java.util.UUID;

/** RabbitMQ message payload — one AI scoring job, dispatched after a host's scoring request. */
public record AiScoringJob(
        UUID answerPublicId,
        UUID attemptPublicId,
        UUID sessionPublicId,
        UUID tenantId,
        String taskType,
        String payload,
        String referenceText) {
}
