package com.pte.notification.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.notification.constant.NotificationConstants;
import com.pte.notification.domain.ProcessedEvent;
import com.pte.notification.domain.UserDirectoryEntry;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.messaging.consumer.dto.ViolationDetectedEvent;
import com.pte.notification.repository.ProcessedEventRepository;
import com.pte.notification.repository.UserDirectoryRepository;
import com.pte.notification.service.NotificationDispatchService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

/**
 * {@code ViolationDetectedEvent} carries no specific recipient (proctor's
 * event only has tenantId/attemptPublicId/sessionPublicId) — fans out to
 * every {@code HOST_ADMIN} in that tenant instead of a single resolved
 * student, unlike the other two event-triggered consumers (phase-11 Design
 * Constraints).
 */
@Component
public class ViolationNotificationConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final NotificationDispatchService dispatchService;
    private final ObjectMapper objectMapper;

    public ViolationNotificationConsumer(ProcessedEventRepository processedEventRepository,
                                         UserDirectoryRepository userDirectoryRepository,
                                         NotificationDispatchService dispatchService, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.userDirectoryRepository = userDirectoryRepository;
        this.dispatchService = dispatchService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = NotificationConstants.TOPIC_VIOLATION_EVENTS, groupId = "notification-violation")
    @Transactional
    public void onViolationEvent(ConsumerRecord<String, String> record) throws IOException {
        UUID eventId = KafkaHeaders.require(record, "id");
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = KafkaHeaders.requireString(record, NotificationConstants.KAFKA_HEADER_EVENT_TYPE);
        if (NotificationConstants.INCOMING_EVENT_VIOLATION_DETECTED.equals(eventType)) {
            notifyHostAdmins(record.value());
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void notifyHostAdmins(String payload) throws IOException {
        ViolationDetectedEvent event = objectMapper.readValue(payload, ViolationDetectedEvent.class);
        String subject = "Violation flagged: " + event.violationType();
        String body = "A proctor flagged attempt " + event.attemptPublicId() + " in session " + event.sessionPublicId()
                + " for " + event.violationType() + (event.detail() == null ? "" : (" — " + event.detail())) + ".";

        for (UserDirectoryEntry hostAdmin : userDirectoryRepository
                .findByTenantIdAndRolesContaining(event.tenantId(), NotificationConstants.ROLE_HOST_ADMIN)) {
            dispatchService.dispatchTo(NotificationType.VIOLATION_DETECTED, hostAdmin, event.tenantId(), subject, body);
        }
    }
}
