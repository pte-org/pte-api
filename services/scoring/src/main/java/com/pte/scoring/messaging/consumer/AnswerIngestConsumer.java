package com.pte.scoring.messaging.consumer;

import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ProcessedEvent;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.messaging.consumer.dto.AnswerSubmittedEvent;
import com.pte.scoring.repository.ProcessedEventRepository;
import com.pte.scoring.repository.ScoringAnswerRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Accumulates exam-delivery's {@code AnswerSubmitted} events into scoring's own
 * {@link ScoringAnswer} projection — pure ingestion, never scores anything
 * itself. Idempotent (ADR-002): dedups by the producer's outbox row id
 * (delivered as the AMQP {@code messageId}, via the polling outbox relay +
 * RabbitMQ, superseding Debezium's outbox router) via {@link ProcessedEvent};
 * the DB unique constraint on {@code answer_public_id} is the second,
 * defense-in-depth line against a duplicate insert slipping through — isolated
 * in its own {@code REQUIRES_NEW} transaction (code-review finding, fixed):
 * on Postgres a failed statement aborts the WHOLE surrounding transaction at
 * the connection level, not just the Java exception, so catching {@link
 * DataIntegrityViolationException} without isolating it would poison this
 * method's own ambient {@code @Transactional} listener transaction, failing
 * the subsequent {@code ProcessedEvent} save/commit instead of no-op'ing.
 *
 * <p>Ordering-sensitive: bound to a SINGLE queue consumed with concurrency=1
 * ({@link com.pte.scoring.messaging.OutboxRabbitMqConfig}), so answers for the
 * same attempt are ingested in submission order.
 */
@Component
public class AnswerIngestConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final ScoringAnswerRepository scoringAnswerRepository;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public AnswerIngestConsumer(ProcessedEventRepository processedEventRepository,
                                ScoringAnswerRepository scoringAnswerRepository, JsonMapper jsonMapper,
                                PlatformTransactionManager transactionManager) {
        this.processedEventRepository = processedEventRepository;
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.jsonMapper = jsonMapper;
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager, definition);
    }

    @RabbitListener(queues = ScoringConstants.QUEUE_ANSWER_INGEST, containerFactory = "answerIngestListenerContainerFactory")
    @Transactional
    public void onAttemptEvent(Message message) {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(properties.getMessageId());
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(properties, ScoringConstants.EVENT_TYPE_HEADER);
        if (ScoringConstants.INCOMING_EVENT_ANSWER_SUBMITTED.equals(eventType)) {
            ingest(new String(message.getBody(), StandardCharsets.UTF_8));
        }
        // AttemptSubmitted and any future event type on this queue: not needed by scoring, ignored.

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void ingest(String payload) {
        AnswerSubmittedEvent event = jsonMapper.readValue(payload, AnswerSubmittedEvent.class);
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(event.answerPublicId());
        answer.setAttemptPublicId(event.attemptPublicId());
        answer.setPinnedItemPublicId(event.pinnedItemPublicId());
        answer.setSessionPublicId(event.sessionPublicId());
        answer.setTenantId(event.tenantId());
        answer.setTaskType(event.taskType());
        answer.setPayload(event.payload());
        answer.setCorrectAnswerText(event.correctAnswerText());
        answer.setOptionsJson(event.optionsJson());
        answer.setExpired(event.expired());
        try {
            requiresNewTransactionTemplate.executeWithoutResult(status -> scoringAnswerRepository.save(answer));
        } catch (DataIntegrityViolationException ex) {
            // Defense-in-depth duplicate (see class javadoc) — ProcessedEvent already dedups the normal case.
            // The REQUIRES_NEW transaction above already rolled back cleanly on its own
            // connection; this method's own ambient @Transactional listener transaction is unaffected.
        }
    }

    private String headerValue(MessageProperties properties, String key) {
        Object value = properties.getHeaders().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing AMQP header '" + key + "' on attempt event");
        }
        return value.toString();
    }
}
