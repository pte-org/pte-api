package com.pte.reporting.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.reporting.constant.ReportingConstants;
import com.pte.reporting.domain.AnswerProjection;
import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.domain.ProcessedEvent;
import com.pte.reporting.messaging.consumer.dto.AnswerSubmittedEvent;
import com.pte.reporting.messaging.consumer.dto.AttemptSubmittedEvent;
import com.pte.reporting.repository.AnswerProjectionRepository;
import com.pte.reporting.repository.AttemptReportRepository;
import com.pte.reporting.repository.ProcessedEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

/**
 * Ingests exam-delivery's attempt lifecycle into reporting's own projection —
 * {@code AttemptSubmitted} creates the {@link AttemptReport} row (unpublished);
 * {@code AnswerSubmitted} creates an unscored {@link AnswerProjection} row.
 * Idempotent (ADR-002): dedups by the producer's outbox row id.
 */
@Component
public class AttemptIngestConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final AttemptReportRepository attemptReportRepository;
    private final AnswerProjectionRepository answerProjectionRepository;
    private final ObjectMapper objectMapper;

    public AttemptIngestConsumer(ProcessedEventRepository processedEventRepository,
                                 AttemptReportRepository attemptReportRepository,
                                 AnswerProjectionRepository answerProjectionRepository, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.attemptReportRepository = attemptReportRepository;
        this.answerProjectionRepository = answerProjectionRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = ReportingConstants.TOPIC_ATTEMPT_EVENTS, groupId = "reporting-attempt-ingest")
    @Transactional
    public void onAttemptEvent(ConsumerRecord<String, String> record) throws IOException {
        UUID eventId = KafkaHeaders.require(record, "id");
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = KafkaHeaders.requireString(record, ReportingConstants.KAFKA_HEADER_EVENT_TYPE);
        if (ReportingConstants.INCOMING_EVENT_ATTEMPT_SUBMITTED.equals(eventType)) {
            ingestAttempt(record.value());
        } else if (ReportingConstants.INCOMING_EVENT_ANSWER_SUBMITTED.equals(eventType)) {
            ingestAnswer(record.value());
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void ingestAttempt(String payload) throws IOException {
        AttemptSubmittedEvent event = objectMapper.readValue(payload, AttemptSubmittedEvent.class);
        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(event.attemptPublicId());
        report.setSessionPublicId(event.sessionPublicId());
        report.setStudentPublicId(event.studentPublicId());
        report.setTenantId(event.tenantId());
        try {
            attemptReportRepository.save(report);
        } catch (DataIntegrityViolationException ex) {
            // Defense-in-depth duplicate — ProcessedEvent already dedups the normal case.
        }
    }

    private void ingestAnswer(String payload) throws IOException {
        AnswerSubmittedEvent event = objectMapper.readValue(payload, AnswerSubmittedEvent.class);
        AnswerProjection answer = new AnswerProjection();
        answer.setAnswerPublicId(event.answerPublicId());
        answer.setAttemptPublicId(event.attemptPublicId());
        answer.setTaskType(event.taskType());
        try {
            answerProjectionRepository.save(answer);
        } catch (DataIntegrityViolationException ex) {
            // Defense-in-depth duplicate — ProcessedEvent already dedups the normal case.
        }
    }
}
