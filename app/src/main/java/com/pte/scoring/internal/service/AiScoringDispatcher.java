package com.pte.scoring.internal.service;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Routes AI-scorable answers to the RabbitMQ work queue. Each dispatch marks
 * the row {@code AI_SCORING} with a fresh dispatch token, and only a result
 * carrying that token is accepted, so a job from an older dispatch can never
 * overwrite a newer one.
 *
 * <p>The job is sent after the host's transaction commits, so a worker never
 * sees a row that is not yet {@code AI_SCORING}, and a rollback sends nothing.
 * If the broker is down at that moment the row stays {@code AI_SCORING} with
 * nothing queued; once it is older than {@link #staleBefore} the host's next
 * scoring request sends it again.
 */
@Service
public class AiScoringDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AiScoringDispatcher.class);

    private final AiEvaluationRepository scoringAnswerRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final AiProviderProperties properties;

    public AiScoringDispatcher(AiEvaluationRepository scoringAnswerRepository, RabbitTemplate rabbitTemplate,
                               Clock clock, AiProviderProperties properties) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
        this.properties = properties;
    }

    public boolean supports(ScoringMethod scoringMethod) {
        return scoringMethod == ScoringMethod.AI_SPEECH || scoringMethod == ScoringMethod.AI_TEXT;
    }

    /** {@code scoringMethod} is the caller's ({@code ScoringCommandService}) already-resolved value — this class never re-resolves it. */
    public void dispatch(AiEvaluation answer, ScoringMethod scoringMethod) {
        if (!supports(scoringMethod)) {
            throw new IllegalArgumentException(
                    String.format(ScoringConstants.UNSUPPORTED_AI_TASK_TYPE, answer.getTaskType()));
        }
        long token = answer.markAiDispatched(clock.instant());
        scoringAnswerRepository.save(answer);
        sendAfterCommit(new AiScoringJob(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getTenantId(), scoringMethod.name(), token));
    }

    /**
     * An {@code AI_SCORING} row dispatched before this instant should have
     * finished long ago: its job was lost to a broker outage or a crash.
     */
    public Instant staleBefore() {
        return clock.instant().minus(Duration.ofMinutes(properties.getStaleDispatchMinutes()));
    }

    private void sendAfterCommit(AiScoringJob job) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(job);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(job);
            }
        });
    }

    private void send(AiScoringJob job) {
        try {
            rabbitTemplate.convertAndSend(ScoringConstants.AI_SCORING_EXCHANGE,
                    ScoringConstants.AI_SCORING_ROUTING_KEY, job);
        } catch (AmqpException ex) {
            // The row is already committed as AI_SCORING; a request after staleBefore sends it again.
            log.warn("AI scoring job for answer {} was not queued: {}", job.answerPublicId(),
                    ex.getClass().getSimpleName());
        }
    }
}
