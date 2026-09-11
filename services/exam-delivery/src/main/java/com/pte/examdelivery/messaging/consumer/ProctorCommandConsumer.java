package com.pte.examdelivery.messaging.consumer;

import com.pte.examdelivery.constant.ExamDeliveryConstants;
import com.pte.examdelivery.domain.ProcessedEvent;
import com.pte.examdelivery.messaging.consumer.dto.ProctorCommandEvent;
import com.pte.examdelivery.repository.ProcessedEventRepository;
import com.pte.examdelivery.service.ProctorCommandService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * exam-delivery's first consumer: applies proctor's FORCE_SUBMIT command via
 * the polling outbox relay + RabbitMQ (rabbitmq-outbox-migration Phase 5,
 * superseding Debezium's outbox router). Idempotent, same dedup pattern as
 * every other consumer in this codebase (ADR-002) — checked before apply,
 * saved after, inside one transaction.
 *
 * <p>EXTEND_TIME removed (client-side-exam-timer Phase 5) — accepted
 * capability loss, see {@code ProctorCommandEvent}'s doc comment.
 *
 * <p>Ordering-sensitive: bound to a SINGLE queue consumed with concurrency=1
 * ({@link com.pte.examdelivery.messaging.RabbitMqConfig}), so two commands
 * for the same attempt apply in the order proctor issued them.
 */
@Component
public class ProctorCommandConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final ProctorCommandService proctorCommandService;
    private final JsonMapper jsonMapper;

    public ProctorCommandConsumer(ProcessedEventRepository processedEventRepository,
                                  ProctorCommandService proctorCommandService, JsonMapper jsonMapper) {
        this.processedEventRepository = processedEventRepository;
        this.proctorCommandService = proctorCommandService;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = ExamDeliveryConstants.QUEUE_PROCTOR_COMMANDS)
    @Transactional
    public void onProctorCommand(Message message) {
        UUID eventId = UUID.fromString(message.getMessageProperties().getMessageId());
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        ProctorCommandEvent event = jsonMapper.readValue(payload, ProctorCommandEvent.class);
        applyCommand(event);

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void applyCommand(ProctorCommandEvent event) {
        if (ExamDeliveryConstants.COMMAND_TYPE_FORCE_SUBMIT.equals(event.commandType())) {
            proctorCommandService.forceSubmit(event.attemptPublicId(), event.tenantId());
        }
        // Any other/unknown commandType (including a stray EXTEND_TIME from an in-flight message —
        // see ProctorCommandEvent's doc comment): ignored, not an error.
    }
}
