package com.pte.notification.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.notification.constant.NotificationConstants;
import com.pte.notification.domain.ProcessedEvent;
import com.pte.notification.domain.UserDirectoryEntry;
import com.pte.notification.messaging.consumer.dto.UserCreatedEvent;
import com.pte.notification.repository.ProcessedEventRepository;
import com.pte.notification.repository.UserDirectoryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;

/**
 * Builds notification's local email directory from iam's {@code UserCreated}
 * — the intended consumer per {@code UserCreatedEvent}'s own javadoc in iam.
 * Never a sync call to iam (phase-11 Design Constraints).
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

    @KafkaListener(topics = NotificationConstants.TOPIC_USER_EVENTS, groupId = "notification-user-directory")
    @Transactional
    public void onUserEvent(ConsumerRecord<String, String> record) throws IOException {
        UUID eventId = KafkaHeaders.require(record, "id");
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = KafkaHeaders.requireString(record, NotificationConstants.KAFKA_HEADER_EVENT_TYPE);
        if (NotificationConstants.INCOMING_EVENT_USER_CREATED.equals(eventType)) {
            upsertDirectoryEntry(record.value());
        }
        // UserSuspended and any future event type on this topic: not this consumer's concern, ignored.

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
}
