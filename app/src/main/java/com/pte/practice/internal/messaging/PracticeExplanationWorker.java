package com.pte.practice.internal.messaging;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.service.PracticeExplanationJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Consumes durable job identity only; all answer content is read from the database. */
@Component
@ConditionalOnProperty(name = PracticeConstants.PRACTICE_AI_ENABLED_PROPERTY, havingValue = "true")
public class PracticeExplanationWorker {

    private static final Logger LOG = LoggerFactory.getLogger(PracticeExplanationWorker.class);
    private final PracticeExplanationJobService jobService;

    public PracticeExplanationWorker(PracticeExplanationJobService jobService) {
        this.jobService = jobService;
    }

    @RabbitListener(queues = PracticeConstants.PRACTICE_EXPLANATION_QUEUE,
            containerFactory = "practiceExplanationRabbitListenerContainerFactory")
    public void onJob(PracticeExplanationJobMessage message) {
        if (message == null || message.jobPublicId() == null) {
            return;
        }
        jobService.process(message.jobPublicId());
    }

    @RabbitListener(queues = PracticeConstants.PRACTICE_EXPLANATION_DLQ,
            containerFactory = "practiceExplanationRabbitListenerContainerFactory")
    public void onDeadLettered(PracticeExplanationJobMessage message) {
        if (message != null && message.jobPublicId() != null) {
            jobService.markDeadLettered(message.jobPublicId(), message.runToken(), message.attemptCount());
        }
    }

    @Scheduled(fixedDelayString = "${practice.ai.poll-delay-ms:1000}")
    public void recoverExpiredLeases() {
        try {
            jobService.recoverExpired();
        } catch (RuntimeException failure) {
            LOG.warn("Practice explanation lease recovery deferred: errorType={}",
                    failure.getClass().getSimpleName());
        }
    }
}
