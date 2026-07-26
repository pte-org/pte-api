package com.pte.scoring.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ProcessedEvent;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.event.AnswerScoredEvent;
import com.pte.scoring.domain.event.AttemptScoredEvent;
import com.pte.scoring.messaging.consumer.dto.ScoringRequestedEvent;
import com.pte.scoring.messaging.outbox.OutboxWriter;
import com.pte.scoring.repository.ProcessedEventRepository;
import com.pte.scoring.repository.ScoringAnswerRepository;
import com.pte.scoring.service.ObjectiveScoringService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Executes the host's {@code ScoringRequested} command (ADR-002 host-gated
 * model — scoring NEVER auto-triggers on submit). Scores every {@code PENDING}
 * answer in the session that {@link ObjectiveScoringService} currently
 * supports; unsupported types (speaking/writing, pending Phase 9) stay
 * {@code PENDING} — honest completion, not a fake "skipped" status.
 * Idempotent (ADR-002): dedups by the producer's outbox row id.
 */
@Component
public class ScoringCommandConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final ScoringAnswerRepository scoringAnswerRepository;
    private final ObjectiveScoringService objectiveScoringService;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public ScoringCommandConsumer(ProcessedEventRepository processedEventRepository,
                                  ScoringAnswerRepository scoringAnswerRepository,
                                  ObjectiveScoringService objectiveScoringService, OutboxWriter outboxWriter,
                                  ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.objectiveScoringService = objectiveScoringService;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = ScoringConstants.TOPIC_SESSION_EVENTS, groupId = "scoring-command")
    @Transactional
    public void onSessionEvent(ConsumerRecord<String, String> record) throws IOException {
        UUID eventId = KafkaHeaders.require(record, "id");
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = KafkaHeaders.requireString(record, ScoringConstants.KAFKA_HEADER_EVENT_TYPE);
        if (ScoringConstants.INCOMING_EVENT_SCORING_REQUESTED.equals(eventType)) {
            handleScoringRequested(record.value());
        }
        // PublishRequested and any future event type on this topic: not scoring's concern, ignored.

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void handleScoringRequested(String payload) throws IOException {
        ScoringRequestedEvent event = objectMapper.readValue(payload, ScoringRequestedEvent.class);
        List<ScoringAnswer> pending = scoringAnswerRepository
                .findBySessionPublicIdAndStatus(event.sessionPublicId(), ScoringAnswerStatus.PENDING);

        Set<UUID> touchedAttempts = new HashSet<>();
        for (ScoringAnswer answer : pending) {
            if (!objectiveScoringService.supports(answer.getTaskType())) {
                continue;
            }
            int rawScore = objectiveScoringService.score(answer);
            answer.markScored(rawScore);
            scoringAnswerRepository.save(answer);
            touchedAttempts.add(answer.getAttemptPublicId());

            outboxWriter.write(ScoringConstants.AGGREGATE_ANSWER, answer.getAnswerPublicId().toString(),
                    ScoringConstants.EVENT_ANSWER_SCORED,
                    new AnswerScoredEvent(answer.getAttemptPublicId(), answer.getAnswerPublicId(),
                            answer.getTenantId(), rawScore),
                    answer.getTenantId());
        }

        for (UUID attemptPublicId : touchedAttempts) {
            long stillPending = scoringAnswerRepository
                    .countByAttemptPublicIdAndStatus(attemptPublicId, ScoringAnswerStatus.PENDING);
            if (stillPending == 0) {
                outboxWriter.write(ScoringConstants.AGGREGATE_ATTEMPT, attemptPublicId.toString(),
                        ScoringConstants.EVENT_ATTEMPT_SCORED,
                        new AttemptScoredEvent(attemptPublicId, event.sessionPublicId(), event.tenantId()),
                        event.tenantId());
            }
        }
    }
}
