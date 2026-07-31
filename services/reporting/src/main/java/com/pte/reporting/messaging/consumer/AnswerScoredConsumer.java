package com.pte.reporting.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.reporting.constant.ReportingConstants;
import com.pte.reporting.domain.AnswerProjection;
import com.pte.reporting.domain.ProcessedEvent;
import com.pte.reporting.messaging.consumer.dto.AnswerScoredEvent;
import com.pte.reporting.repository.ProcessedEventRepository;
import com.pte.reporting.service.AnswerScoreService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Applies scoring's {@code AnswerScored} to the matching {@link AnswerProjection}.
 * If the answer hasn't been ingested yet (event arrived out of order relative to
 * exam-delivery's {@code AnswerSubmitted}), it's a no-op — {@code AnswerSubmitted}
 * always precedes {@code AnswerScored} in practice (scoring only ingests after
 * submission), but this stays defensive rather than assuming ordering. Idempotent
 * (ADR-002): dedups by the producer's outbox row id (delivered as the AMQP
 * {@code messageId}, via the polling outbox relay + RabbitMQ, superseding
 * Debezium's outbox router). Upsert logic itself lives in {@link
 * AnswerScoreService}, shared with the read-model rebuild orchestration
 * (rabbitmq-outbox-migration Phase 9) so both paths can never silently diverge.
 */
@Component
public class AnswerScoredConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final AnswerScoreService answerScoreService;
    private final ObjectMapper objectMapper;

    public AnswerScoredConsumer(ProcessedEventRepository processedEventRepository,
                                AnswerScoreService answerScoreService, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.answerScoreService = answerScoreService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = ReportingConstants.QUEUE_ANSWER_SCORED)
    @Transactional
    public void onScoringAnswerEvent(Message message) throws IOException {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(properties.getMessageId());
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(properties, ReportingConstants.EVENT_TYPE_HEADER);
        if (ReportingConstants.INCOMING_EVENT_ANSWER_SCORED.equals(eventType)) {
            applyScore(new String(message.getBody(), StandardCharsets.UTF_8));
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void applyScore(String payload) throws IOException {
        answerScoreService.applyScore(objectMapper.readValue(payload, AnswerScoredEvent.class));
    }

    private String headerValue(MessageProperties properties, String key) {
        Object value = properties.getHeaders().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing AMQP header '" + key + "' on scoring answer event");
        }
        return value.toString();
    }
}
