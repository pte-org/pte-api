package com.pte.reporting.messaging.consumer;

import com.pte.reporting.constant.ReportingConstants;
import com.pte.reporting.domain.AnswerProjection;
import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.domain.ProcessedEvent;
import com.pte.reporting.messaging.consumer.dto.AnswerSubmittedEvent;
import com.pte.reporting.messaging.consumer.dto.AttemptSubmittedEvent;
import com.pte.reporting.repository.ProcessedEventRepository;
import com.pte.reporting.service.AttemptProjectionService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Ingests exam-delivery's attempt lifecycle into reporting's own projection —
 * {@code AttemptSubmitted} creates the {@link AttemptReport} row (unpublished);
 * {@code AnswerSubmitted} creates an unscored {@link AnswerProjection} row.
 * Idempotent (ADR-002): dedups by the producer's outbox row id (delivered as
 * the AMQP {@code messageId}, via the polling outbox relay + RabbitMQ,
 * superseding Debezium's outbox router). Upsert logic itself lives in {@link
 * AttemptProjectionService}, shared with the read-model rebuild orchestration
 * (rabbitmq-outbox-migration Phase 9) so both paths can never silently diverge.
 */
@Component
public class AttemptIngestConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final AttemptProjectionService attemptProjectionService;
    private final JsonMapper jsonMapper;

    public AttemptIngestConsumer(ProcessedEventRepository processedEventRepository,
                                 AttemptProjectionService attemptProjectionService, JsonMapper jsonMapper) {
        this.processedEventRepository = processedEventRepository;
        this.attemptProjectionService = attemptProjectionService;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = ReportingConstants.QUEUE_ATTEMPT_INGEST)
    @Transactional
    public void onAttemptEvent(Message message) {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(properties.getMessageId());
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(properties, ReportingConstants.EVENT_TYPE_HEADER);
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        if (ReportingConstants.INCOMING_EVENT_ATTEMPT_SUBMITTED.equals(eventType)) {
            ingestAttempt(payload);
        } else if (ReportingConstants.INCOMING_EVENT_ANSWER_SUBMITTED.equals(eventType)) {
            ingestAnswer(payload);
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void ingestAttempt(String payload) {
        attemptProjectionService.ingestAttempt(jsonMapper.readValue(payload, AttemptSubmittedEvent.class));
    }

    private void ingestAnswer(String payload) {
        attemptProjectionService.ingestAnswer(jsonMapper.readValue(payload, AnswerSubmittedEvent.class));
    }

    private String headerValue(MessageProperties properties, String key) {
        Object value = properties.getHeaders().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing AMQP header '" + key + "' on attempt event");
        }
        return value.toString();
    }
}
