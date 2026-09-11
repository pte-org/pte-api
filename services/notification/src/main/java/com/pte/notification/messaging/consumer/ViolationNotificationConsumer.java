package com.pte.notification.messaging.consumer;

import com.pte.notification.constant.NotificationConstants;
import com.pte.notification.domain.ProcessedEvent;
import com.pte.notification.domain.UserDirectoryEntry;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.messaging.consumer.dto.ViolationDetectedEvent;
import com.pte.notification.repository.ProcessedEventRepository;
import com.pte.notification.repository.UserDirectoryRepository;
import com.pte.notification.service.NotificationDispatchService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * {@code ViolationDetectedEvent} carries no specific recipient (proctor's
 * event only has tenantId/attemptPublicId/sessionPublicId) — fans out to
 * every {@code HOST_ADMIN} in that tenant instead of a single resolved
 * student, unlike the other two event-triggered consumers (phase-11 Design
 * Constraints). Idempotent (ADR-002): dedups by the producer's outbox row id
 * (delivered as the AMQP {@code messageId}, via the polling outbox relay +
 * RabbitMQ, superseding Debezium's outbox router).
 */
@Component
public class ViolationNotificationConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final NotificationDispatchService dispatchService;
    private final JsonMapper jsonMapper;

    public ViolationNotificationConsumer(ProcessedEventRepository processedEventRepository,
                                         UserDirectoryRepository userDirectoryRepository,
                                         NotificationDispatchService dispatchService, JsonMapper jsonMapper) {
        this.processedEventRepository = processedEventRepository;
        this.userDirectoryRepository = userDirectoryRepository;
        this.dispatchService = dispatchService;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = NotificationConstants.QUEUE_VIOLATION_EVENTS, containerFactory = "eventBackboneListenerContainerFactory")
    @Transactional
    public void onViolationEvent(Message message) {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(properties.getMessageId());
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(properties, NotificationConstants.EVENT_TYPE_HEADER);
        if (NotificationConstants.INCOMING_EVENT_VIOLATION_DETECTED.equals(eventType)) {
            notifyHostAdmins(new String(message.getBody(), StandardCharsets.UTF_8));
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void notifyHostAdmins(String payload) {
        ViolationDetectedEvent event = jsonMapper.readValue(payload, ViolationDetectedEvent.class);
        String subject = "Violation flagged: " + event.violationType();
        String body = "A proctor flagged attempt " + event.attemptPublicId() + " in session " + event.sessionPublicId()
                + " for " + event.violationType() + (event.detail() == null ? "" : (" — " + event.detail())) + ".";

        for (UserDirectoryEntry hostAdmin : userDirectoryRepository
                .findByTenantIdAndRolesContaining(event.tenantId(), NotificationConstants.ROLE_HOST_ADMIN)) {
            dispatchService.dispatchTo(NotificationType.VIOLATION_DETECTED, hostAdmin, event.tenantId(), subject, body);
        }
    }

    private String headerValue(MessageProperties properties, String key) {
        Object value = properties.getHeaders().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing AMQP header '" + key + "' on violation event");
        }
        return value.toString();
    }
}
