package com.pte.scoring.messaging.consumer;

import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ProcessedEvent;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.event.AnswerScoredEvent;
import com.pte.scoring.messaging.consumer.dto.ScoringRequestedEvent;
import com.pte.scoring.messaging.outbox.OutboxWriter;
import com.pte.scoring.repository.ProcessedEventRepository;
import com.pte.scoring.repository.ScoringAnswerRepository;
import com.pte.scoring.service.AiScoringDispatcher;
import com.pte.scoring.service.AttemptCompletionService;
import com.pte.scoring.service.ObjectiveScoringService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Executes the host's {@code ScoringRequested} command (ADR-002 host-gated
 * model — scoring NEVER auto-triggers on submit). For every {@code PENDING}
 * answer in the session: {@link ObjectiveScoringService}-supported types score
 * synchronously; {@link AiScoringDispatcher}-supported types (phase-09: Read
 * Aloud, Write Essay) get queued to RabbitMQ instead; any other type stays
 * {@code PENDING} — honest completion, not a fake "skipped" status.
 * Idempotent (ADR-002): dedups by the producer's outbox row id (delivered as
 * the AMQP {@code messageId}, via the polling outbox relay + RabbitMQ,
 * superseding Debezium's outbox router).
 */
@Component
public class ScoringCommandConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final ScoringAnswerRepository scoringAnswerRepository;
    private final ObjectiveScoringService objectiveScoringService;
    private final AiScoringDispatcher aiScoringDispatcher;
    private final AttemptCompletionService attemptCompletionService;
    private final OutboxWriter outboxWriter;
    private final JsonMapper jsonMapper;

    public ScoringCommandConsumer(ProcessedEventRepository processedEventRepository,
                                  ScoringAnswerRepository scoringAnswerRepository,
                                  ObjectiveScoringService objectiveScoringService,
                                  AiScoringDispatcher aiScoringDispatcher,
                                  AttemptCompletionService attemptCompletionService, OutboxWriter outboxWriter,
                                  JsonMapper jsonMapper) {
        this.processedEventRepository = processedEventRepository;
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.objectiveScoringService = objectiveScoringService;
        this.aiScoringDispatcher = aiScoringDispatcher;
        this.attemptCompletionService = attemptCompletionService;
        this.outboxWriter = outboxWriter;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = ScoringConstants.QUEUE_SCORING_COMMAND, containerFactory = "scoringCommandListenerContainerFactory")
    @Transactional
    public void onSessionEvent(Message message) {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(properties.getMessageId());
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(properties, ScoringConstants.EVENT_TYPE_HEADER);
        if (ScoringConstants.INCOMING_EVENT_SCORING_REQUESTED.equals(eventType)) {
            handleScoringRequested(new String(message.getBody(), StandardCharsets.UTF_8));
        }
        // PublishRequested and any future event type on this queue: not scoring's concern, ignored.

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void handleScoringRequested(String payload) {
        ScoringRequestedEvent event = jsonMapper.readValue(payload, ScoringRequestedEvent.class);
        List<ScoringAnswer> pending = scoringAnswerRepository
                .findBySessionPublicIdAndStatus(event.sessionPublicId(), ScoringAnswerStatus.PENDING);

        Set<UUID> touchedAttempts = new HashSet<>();
        for (ScoringAnswer answer : pending) {
            if (objectiveScoringService.supports(answer.getTaskType())) {
                scoreObjectively(answer);
                touchedAttempts.add(answer.getAttemptPublicId());
            } else if (aiScoringDispatcher.supports(answer.getTaskType())) {
                aiScoringDispatcher.dispatch(answer);
                // Not touchedAttempts here: dispatch moves the row to AI_SCORING, still
                // non-terminal — completion is checked later by the worker/review step.
            }
            // Any other type: stays PENDING (honest completion, Phase 7).
        }

        for (UUID attemptPublicId : touchedAttempts) {
            attemptCompletionService.checkAndEmitIfComplete(attemptPublicId, event.sessionPublicId(), event.tenantId());
        }
    }

    private void scoreObjectively(ScoringAnswer answer) {
        int rawScore = objectiveScoringService.score(answer);
        answer.markScored(rawScore);
        scoringAnswerRepository.save(answer);

        outboxWriter.write(ScoringConstants.AGGREGATE_ANSWER, answer.getAnswerPublicId().toString(),
                ScoringConstants.EVENT_ANSWER_SCORED,
                new AnswerScoredEvent(answer.getAttemptPublicId(), answer.getAnswerPublicId(),
                        answer.getTenantId(), rawScore),
                answer.getTenantId());
    }

    private String headerValue(MessageProperties properties, String key) {
        Object value = properties.getHeaders().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing AMQP header '" + key + "' on session event");
        }
        return value.toString();
    }
}
