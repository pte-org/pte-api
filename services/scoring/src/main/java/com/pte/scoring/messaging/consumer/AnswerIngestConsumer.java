package com.pte.scoring.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ProcessedEvent;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.messaging.consumer.dto.AnswerSubmittedEvent;
import com.pte.scoring.repository.ProcessedEventRepository;
import com.pte.scoring.repository.ScoringAnswerRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

/**
 * Accumulates exam-delivery's {@code AnswerSubmitted} events into scoring's own
 * {@link ScoringAnswer} projection — pure ingestion, never scores anything
 * itself. Idempotent (ADR-002): dedups by the producer's outbox row id
 * (Debezium's {@code id} header) via {@link ProcessedEvent}; the DB unique
 * constraint on {@code answer_public_id} is the second, defense-in-depth line
 * against a duplicate insert slipping through.
 */
@Component
public class AnswerIngestConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final ScoringAnswerRepository scoringAnswerRepository;
    private final ObjectMapper objectMapper;

    public AnswerIngestConsumer(ProcessedEventRepository processedEventRepository,
                                ScoringAnswerRepository scoringAnswerRepository, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = ScoringConstants.TOPIC_ATTEMPT_EVENTS, groupId = "scoring-answer-ingest")
    @Transactional
    public void onAttemptEvent(ConsumerRecord<String, String> record) throws IOException {
        UUID eventId = KafkaHeaders.require(record, "id");
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = KafkaHeaders.requireString(record, ScoringConstants.KAFKA_HEADER_EVENT_TYPE);
        if (ScoringConstants.INCOMING_EVENT_ANSWER_SUBMITTED.equals(eventType)) {
            ingest(record.value());
        }
        // AttemptSubmitted and any future event type on this topic: not needed by scoring, ignored.

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void ingest(String payload) throws IOException {
        AnswerSubmittedEvent event = objectMapper.readValue(payload, AnswerSubmittedEvent.class);
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
            scoringAnswerRepository.save(answer);
        } catch (DataIntegrityViolationException ex) {
            // Defense-in-depth duplicate (see class javadoc) — ProcessedEvent already dedups the normal case.
        }
    }
}
