package com.pte.notification.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.notification.constant.NotificationConstants;
import com.pte.notification.domain.ProcessedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.messaging.consumer.dto.StudentEnrolledEvent;
import com.pte.notification.repository.ProcessedEventRepository;
import com.pte.notification.service.NotificationDispatchService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * scheduling's outbox exchange is shared with {@code SessionScheduled}/
 * {@code ScoringRequested}/{@code PublishRequested} — this consumer only acts
 * on {@code StudentEnrolled}, same filter-by-header pattern as every other
 * shared-exchange consumer in this codebase (e.g. scoring's ScoringCommandConsumer).
 * Idempotent (ADR-002): dedups by the producer's outbox row id (delivered as
 * the AMQP {@code messageId}, via the polling outbox relay + RabbitMQ,
 * superseding Debezium's outbox router).
 */
@Component
public class EnrollmentNotificationConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationDispatchService dispatchService;
    private final ObjectMapper objectMapper;

    public EnrollmentNotificationConsumer(ProcessedEventRepository processedEventRepository,
                                          NotificationDispatchService dispatchService, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.dispatchService = dispatchService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = NotificationConstants.QUEUE_SESSION_EVENTS, containerFactory = "eventBackboneListenerContainerFactory")
    @Transactional
    public void onSessionEvent(Message message) throws IOException {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(properties.getMessageId());
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(properties, NotificationConstants.EVENT_TYPE_HEADER);
        if (NotificationConstants.INCOMING_EVENT_STUDENT_ENROLLED.equals(eventType)) {
            notifyStudent(new String(message.getBody(), StandardCharsets.UTF_8));
        }
        // SessionScheduled/ScoringRequested/PublishRequested: not this consumer's concern, ignored.

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void notifyStudent(String payload) throws IOException {
        StudentEnrolledEvent event = objectMapper.readValue(payload, StudentEnrolledEvent.class);
        dispatchService.dispatch(NotificationType.STUDENT_ENROLLED, event.studentPublicId(), event.tenantId(),
                "You've been enrolled in an exam session",
                "You've been enrolled in exam session " + event.sessionPublicId() + ". Log in to view your schedule.");
    }

    private String headerValue(MessageProperties properties, String key) {
        Object value = properties.getHeaders().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing AMQP header '" + key + "' on session event");
        }
        return value.toString();
    }
}
