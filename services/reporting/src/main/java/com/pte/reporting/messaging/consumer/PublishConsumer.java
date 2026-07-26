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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Executes the host's {@code PublishRequested} command (session-level) by
 * marking every {@link AttemptReport} in that session {@code published=true} —
 * this IS the visibility gate (phase-08 design constraint: publish is a gate,
 * not a data freeze; the report keeps reflecting live projection state after
 * publish). Emits {@code AttemptPublished} per attempt for future consumers.
 * Idempotent (ADR-002): dedups by the producer's outbox row id.
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

    @KafkaListener(topics = ReportingConstants.TOPIC_SESSION_EVENTS, groupId = "reporting-publish")
    @Transactional
    public void onSessionEvent(ConsumerRecord<String, String> record) throws IOException {
        UUID eventId = KafkaHeaders.require(record, "id");
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = KafkaHeaders.requireString(record, ReportingConstants.KAFKA_HEADER_EVENT_TYPE);
        if (ReportingConstants.INCOMING_EVENT_PUBLISH_REQUESTED.equals(eventType)) {
            publishSession(record.value());
        }
        // ScoringRequested and any future event type on this topic: not reporting's concern, ignored.

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
}
