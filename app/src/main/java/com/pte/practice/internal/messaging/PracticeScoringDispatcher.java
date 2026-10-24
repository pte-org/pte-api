package com.pte.practice.internal.messaging;

import com.pte.practice.internal.constant.PracticeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends a practice scoring job once the transaction that marked the item SCORING has committed, so a worker never
 * sees an item that is not yet SCORING and a rollback sends nothing. If the broker is down at that moment the item
 * stays SCORING with nothing queued; the stale re-dispatch on the next poll sends it again.
 */
@Component
public class PracticeScoringDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PracticeScoringDispatcher.class);

    private final RabbitTemplate rabbitTemplate;

    public PracticeScoringDispatcher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void dispatchAfterCommit(PracticeScoringJob job) {
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

    private void send(PracticeScoringJob job) {
        try {
            rabbitTemplate.convertAndSend(PracticeConstants.PRACTICE_SCORING_EXCHANGE,
                    PracticeConstants.PRACTICE_SCORING_ROUTING_KEY, job);
        } catch (AmqpException ex) {
            log.warn("Practice scoring job for item {} was not queued: {}", job.itemPublicId(),
                    ex.getClass().getSimpleName());
        }
    }
}
