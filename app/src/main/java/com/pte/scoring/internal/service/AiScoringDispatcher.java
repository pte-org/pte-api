package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Routes AI-scorable {@code PENDING} answers to the RabbitMQ work queue
 * instead of leaving them silently pending like a genuinely-unsupported type.
 *
 * <p><b>Known gap, documented not silent:</b> this publish happens inside the
 * same DB transaction as the status update, no outbox in between — a crash
 * between {@code convertAndSend} succeeding and the transaction committing
 * could leave a message in flight for a row still showing {@code PENDING}.
 * {@code AiScoringWorker} is written to be self-healing against this (it reads
 * the row fresh and doesn't assert {@code AI_SCORING} as a precondition), so
 * the race doesn't corrupt state.
 */
@Service
public class AiScoringDispatcher {

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final RabbitTemplate rabbitTemplate;

    public AiScoringDispatcher(ScoringAnswerRepository scoringAnswerRepository, RabbitTemplate rabbitTemplate) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    public boolean supports(String taskType) {
        return AiScoringTaskCatalog.supports(taskType);
    }

    public void dispatch(ScoringAnswer answer) {
        if (!supports(answer.getTaskType())) {
            throw new IllegalArgumentException("Unsupported AI task type: " + answer.getTaskType());
        }
        answer.setStatus(ScoringAnswerStatus.AI_SCORING);
        scoringAnswerRepository.save(answer);

        AiScoringJob job = new AiScoringJob(
                answer.getAnswerPublicId(), answer.getAttemptPublicId(), answer.getSessionPublicId(),
                answer.getTenantId(), answer.getTaskType(), answer.getPayload(), answer.getCorrectAnswerText());
        rabbitTemplate.convertAndSend(ScoringConstants.AI_SCORING_EXCHANGE, ScoringConstants.AI_SCORING_ROUTING_KEY, job);
    }
}
