package com.pte.reporting.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.reporting.constant.ReportingConstants;
import com.pte.reporting.domain.AnswerProjection;
import com.pte.reporting.domain.ProcessedEvent;
import com.pte.reporting.messaging.consumer.dto.AnswerScoredEvent;
import com.pte.reporting.repository.AnswerProjectionRepository;
import com.pte.reporting.repository.ProcessedEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

/**
 * Applies scoring's {@code AnswerScored} to the matching {@link AnswerProjection}.
 * If the answer hasn't been ingested yet (event arrived out of order relative to
 * exam-delivery's {@code AnswerSubmitted}), it's a no-op — {@code AnswerSubmitted}
 * always precedes {@code AnswerScored} in practice (scoring only ingests after
 * submission), but this stays defensive rather than assuming ordering. Idempotent
 * (ADR-002): dedups by the producer's outbox row id.
 */
@Component
public class AnswerScoredConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final AnswerProjectionRepository answerProjectionRepository;
    private final ObjectMapper objectMapper;

    public AnswerScoredConsumer(ProcessedEventRepository processedEventRepository,
                                AnswerProjectionRepository answerProjectionRepository, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.answerProjectionRepository = answerProjectionRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = ReportingConstants.TOPIC_SCORING_ANSWER_EVENTS, groupId = "reporting-answer-scored")
    @Transactional
    public void onScoringAnswerEvent(ConsumerRecord<String, String> record) throws IOException {
        UUID eventId = KafkaHeaders.require(record, "id");
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = KafkaHeaders.requireString(record, ReportingConstants.KAFKA_HEADER_EVENT_TYPE);
        if (ReportingConstants.INCOMING_EVENT_ANSWER_SCORED.equals(eventType)) {
            applyScore(record.value());
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void applyScore(String payload) throws IOException {
        AnswerScoredEvent event = objectMapper.readValue(payload, AnswerScoredEvent.class);
        answerProjectionRepository.findByAnswerPublicId(event.answerPublicId())
                .ifPresent(answer -> {
                    answer.applyScore(event.rawScore());
                    answerProjectionRepository.save(answer);
                });
    }
}
