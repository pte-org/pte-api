package com.pte.reporting.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.reporting.constant.ReportingConstants;
import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.domain.ProcessedEvent;
import com.pte.reporting.domain.event.AttemptPublishedEvent;
import com.pte.reporting.messaging.consumer.dto.PublishRequestedEvent;
import com.pte.reporting.messaging.outbox.OutboxWriter;
import com.pte.reporting.repository.AttemptReportRepository;
import com.pte.reporting.repository.ProcessedEventRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Executes the host's {@code PublishRequested} command (session-level) by
 * marking every {@link AttemptReport} in that session {@code published=true} —
 * this IS the visibility gate (phase-08 design constraint: publish is a gate,
 * not a data freeze; the report keeps reflecting live projection state after
 * publish). Emits {@code AttemptPublished} per attempt for future consumers.
 * Idempotent (ADR-002): dedups by the producer's outbox row id (delivered as
 * the AMQP {@code messageId}, via the polling outbox relay + RabbitMQ,
 * superseding Debezium's outbox router).
 */
@Component
public class PublishConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final AttemptReportRepository attemptReportRepository;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public PublishConsumer(ProcessedEventRepository processedEventRepository,
                           AttemptReportRepository attemptReportRepository, OutboxWriter outboxWriter,
                           ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.attemptReportRepository = attemptReportRepository;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = ReportingConstants.QUEUE_PUBLISH)
    @Transactional
    public void onSessionEvent(Message message) throws IOException {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(properties.getMessageId());
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(properties, ReportingConstants.EVENT_TYPE_HEADER);
        if (ReportingConstants.INCOMING_EVENT_PUBLISH_REQUESTED.equals(eventType)) {
            publishSession(new String(message.getBody(), StandardCharsets.UTF_8));
        }
        // ScoringRequested and any future event type on this queue: not reporting's concern, ignored.

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void publishSession(String payload) throws IOException {
        PublishRequestedEvent event = objectMapper.readValue(payload, PublishRequestedEvent.class);
        List<AttemptReport> reports = attemptReportRepository.findBySessionPublicId(event.sessionPublicId());
        for (AttemptReport report : reports) {
            if (report.isPublished()) {
                continue;
            }
            report.publish();
            attemptReportRepository.save(report);
            outboxWriter.write(ReportingConstants.AGGREGATE_ATTEMPT_REPORT, report.getAttemptPublicId().toString(),
                    ReportingConstants.EVENT_ATTEMPT_PUBLISHED,
                    new AttemptPublishedEvent(report.getAttemptPublicId(), report.getSessionPublicId(),
                            report.getStudentPublicId(), report.getTenantId()),
                    report.getTenantId());
        }
    }

    private String headerValue(MessageProperties properties, String key) {
        Object value = properties.getHeaders().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing AMQP header '" + key + "' on session event");
        }
        return value.toString();
    }
}
