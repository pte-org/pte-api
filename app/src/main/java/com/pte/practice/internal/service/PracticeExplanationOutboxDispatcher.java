package com.pte.practice.internal.service;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeExplanationOutbox;
import com.pte.practice.internal.messaging.PracticeExplanationJobMessage;
import com.pte.practice.internal.repository.PracticeExplanationJobRepository;
import com.pte.practice.internal.repository.PracticeExplanationOutboxRepository;
import com.pte.scoring.PracticeAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Publishes committed job intents and leaves failed intents for a later retry. */
@Component
@ConditionalOnProperty(name = PracticeConstants.PRACTICE_AI_ENABLED_PROPERTY, havingValue = "true")
public class PracticeExplanationOutboxDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(PracticeExplanationOutboxDispatcher.class);

    private final PracticeExplanationOutboxRepository outboxRepository;
    private final PracticeExplanationJobRepository jobRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectProvider<PracticeAiProperties> propertiesProvider;
    private final Clock clock;

    public PracticeExplanationOutboxDispatcher(PracticeExplanationOutboxRepository outboxRepository,
            PracticeExplanationJobRepository jobRepository, RabbitTemplate rabbitTemplate,
            ObjectProvider<PracticeAiProperties> propertiesProvider, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.jobRepository = jobRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.propertiesProvider = propertiesProvider;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${practice.ai.poll-delay-ms:1000}")
    public void tick() {
        Instant now = clock.instant();
        for (PracticeExplanationOutbox outbox : outboxRepository
                .findByPublishedAtIsNullAndNextAttemptAtLessThanEqualAndDeletedFalseOrderByCreatedAtAsc(
                        now, PageRequest.of(0, 50))) {
            try {
                var job = jobRepository.findByIdAndDeletedFalse(outbox.getJobId())
                        .orElseThrow(() -> new IllegalStateException("PRACTICE_EXPLANATION_JOB_MISSING"));
                rabbitTemplate.convertAndSend(PracticeConstants.PRACTICE_EXPLANATION_EXCHANGE,
                        PracticeConstants.PRACTICE_EXPLANATION_ROUTING_KEY,
                        new PracticeExplanationJobMessage(job.getPublicId(), job.getRunToken(), job.getAttemptCount()));
                outbox.setPublishedAt(now);
                outbox.setLastFailureCode(null);
                outboxRepository.save(outbox);
            } catch (RuntimeException failure) {
                outbox.setAttempts(outbox.getAttempts() + 1);
                outbox.setNextAttemptAt(now.plusSeconds(backoffSeconds(outbox.getAttempts())));
                outbox.setLastFailureCode(failure.getClass().getSimpleName());
                outboxRepository.save(outbox);
                LOG.warn("Practice explanation outbox publish deferred: id={}, errorType={}",
                        outbox.getPublicId(), failure.getClass().getSimpleName());
            }
        }
    }

    private long backoffSeconds(int attempt) {
        PracticeAiProperties properties = propertiesProvider.getIfAvailable();
        int max = properties == null ? 120 : properties.getMaxBackoffSeconds();
        long exponential = 1L << Math.min(Math.max(attempt - 1, 0), 10);
        return Math.min(exponential, max);
    }
}
