package com.pte.notification.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.notification.constant.NotificationConstants;
import com.pte.notification.domain.ProcessedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.messaging.consumer.dto.AttemptPublishedEvent;
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
 * Notifies the student that their report is now visible (reporting's
 * host-gated publish, ADR-002). Idempotent (ADR-002): dedups by the
 * producer's outbox row id (delivered as the AMQP {@code messageId}, via the
 * polling outbox relay + RabbitMQ, superseding Debezium's outbox router).
 */
@Component
public class AttemptPublishedConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationDispatchService dispatchService;
    private final ObjectMapper objectMapper;

    public AttemptPublishedConsumer(ProcessedEventRepository processedEventRepository,
                                    NotificationDispatchService dispatchService, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.dispatchService = dispatchService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = NotificationConstants.QUEUE_ATTEMPT_REPORT_EVENTS, containerFactory = "eventBackboneListenerContainerFactory")
    @Transactional
    public void onAttemptReportEvent(Message message) throws IOException {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(properties.getMessageId());
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(properties, NotificationConstants.EVENT_TYPE_HEADER);
        if (NotificationConstants.INCOMING_EVENT_ATTEMPT_PUBLISHED.equals(eventType)) {
            notifyStudent(new String(message.getBody(), StandardCharsets.UTF_8));
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void notifyStudent(String payload) throws IOException {
        AttemptPublishedEvent event = objectMapper.readValue(payload, AttemptPublishedEvent.class);
        dispatchService.dispatch(NotificationType.ATTEMPT_PUBLISHED, event.studentPublicId(), event.tenantId(),
                "Your exam report is ready",
                "Your report for attempt " + event.attemptPublicId() + " is now available. Log in to view your score.");
    }

    private String headerValue(MessageProperties properties, String key) {
        Object value = properties.getHeaders().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing AMQP header '" + key + "' on attempt report event");
        }
        return value.toString();
    }
}
