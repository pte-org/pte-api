package com.pte.notification.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.notification.constant.NotificationConstants;
import com.pte.notification.domain.ProcessedEvent;
import com.pte.notification.domain.UserDirectoryEntry;
import com.pte.notification.messaging.consumer.dto.UserCreatedEvent;
import com.pte.notification.repository.ProcessedEventRepository;
import com.pte.notification.repository.UserDirectoryRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.UUID;

/**
 * Builds notification's local email directory from iam's {@code UserCreated}
 * — the intended consumer per {@code UserCreatedEvent}'s own javadoc in iam.
 * Never a sync call to iam (phase-11 Design Constraints). Idempotent
 * (ADR-002): dedups by the producer's outbox row id (delivered as the AMQP
 * {@code messageId}, via the polling outbox relay + RabbitMQ, superseding
 * Debezium's outbox router).
 */
@Component
public class UserDirectoryConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final ObjectMapper objectMapper;

    public UserDirectoryConsumer(ProcessedEventRepository processedEventRepository,
                                 UserDirectoryRepository userDirectoryRepository, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.userDirectoryRepository = userDirectoryRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = NotificationConstants.QUEUE_USER_EVENTS, containerFactory = "eventBackboneListenerContainerFactory")
    @Transactional
    public void onUserEvent(Message message) throws IOException {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(properties.getMessageId());
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(properties, NotificationConstants.EVENT_TYPE_HEADER);
        if (NotificationConstants.INCOMING_EVENT_USER_CREATED.equals(eventType)) {
            upsertDirectoryEntry(new String(message.getBody(), StandardCharsets.UTF_8));
        }
        // UserSuspended and any future event type on this queue: not this consumer's concern, ignored.

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void upsertDirectoryEntry(String payload) throws IOException {
        UserCreatedEvent event = objectMapper.readValue(payload, UserCreatedEvent.class);
        UserDirectoryEntry entry = userDirectoryRepository.findByUserPublicId(event.userPublicId())
                .orElseGet(UserDirectoryEntry::new);
        entry.setUserPublicId(event.userPublicId());
        entry.setEmail(event.email());
        entry.setTenantId(event.tenantId());
        entry.setRoles(event.roles() == null ? new HashSet<>() : new HashSet<>(event.roles()));
        userDirectoryRepository.save(entry);
    }

    private String headerValue(MessageProperties properties, String key) {
        Object value = properties.getHeaders().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing AMQP header '" + key + "' on user event");
        }
        return value.toString();
    }
}
