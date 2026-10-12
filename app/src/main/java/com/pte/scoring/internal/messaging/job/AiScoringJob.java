package com.pte.scoring.internal.messaging.job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * RabbitMQ message payload — one AI scoring job, dispatched after a host's
 * scoring request. Carries identifiers only: the worker reads the candidate's
 * answer from the scoring row, so it never sits in the queue, the DLQ or a
 * listener error log. {@code scoringMethod} ({@code "AI_SPEECH"}/{@code
 * "AI_TEXT"}) is resolved once at dispatch time from the pinned {@code
 * ScoreTemplate} (spec FR-07). {@code dispatchToken} is the row's token at
 * dispatch; a message queued before tokens existed reads it as {@code null}
 * and is dropped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiScoringJob(
        UUID answerPublicId,
        UUID attemptPublicId,
        UUID sessionPublicId,
        UUID tenantId,
        String scoringMethod,
        Long dispatchToken) {
}
