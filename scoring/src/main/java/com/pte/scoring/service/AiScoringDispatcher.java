package com.pte.scoring.service;

import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.messaging.job.AiScoringJob;
import com.pte.scoring.repository.ScoringAnswerRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Routes AI-scorable {@code PENDING} answers to the RabbitMQ work queue
 * instead of leaving them silently pending like a genuinely-unsupported type
 * (phase-09 — activates what Phase 7 deferred: a vendor call is slow/unreliable,
 * needs a queue; objective scoring didn't).
 *
 * <p><b>Known gap, documented not silent:</b> unlike the Kafka path (outbox +
 * Debezium CDC guarantees atomicity), this publish happens inside the same
 * DB transaction as the status update with no outbox in between — a crash
 * between {@code convertAndSend} succeeding and the transaction committing
 * could leave a message in flight for a row still showing {@code PENDING}.
 * {@code AiScoringWorker} is written to be self-healing against this (it reads
 * the row fresh and doesn't assert {@code AI_SCORING} as a precondition), so
 * the race doesn't corrupt state — just documented as a narrower guarantee
 * than the Kafka path until a RabbitMQ-outbox bridge is worth building.
 */
@Service
public class AiScoringDispatcher {

    private static final Set<String> AI_SCORABLE_TASK_TYPES = Set.of(
            ScoringConstants.TASK_TYPE_READ_ALOUD, ScoringConstants.TASK_TYPE_WRITE_ESSAY);

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final RabbitTemplate rabbitTemplate;

    public AiScoringDispatcher(ScoringAnswerRepository scoringAnswerRepository, RabbitTemplate rabbitTemplate) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    public boolean supports(String taskType) {
        return AI_SCORABLE_TASK_TYPES.contains(taskType);
    }

    public void dispatch(ScoringAnswer answer) {
        answer.setStatus(ScoringAnswerStatus.AI_SCORING);
        scoringAnswerRepository.save(answer);

        AiScoringJob job = new AiScoringJob(
                answer.getAnswerPublicId(), answer.getAttemptPublicId(), answer.getSessionPublicId(),
                answer.getTenantId(), answer.getTaskType(), answer.getPayload(), answer.getCorrectAnswerText());
        rabbitTemplate.convertAndSend(ScoringConstants.AI_SCORING_EXCHANGE, ScoringConstants.AI_SCORING_ROUTING_KEY, job);
    }
}
