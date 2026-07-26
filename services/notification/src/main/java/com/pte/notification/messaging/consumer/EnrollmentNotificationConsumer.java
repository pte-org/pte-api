package com.pte.notification.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.notification.constant.NotificationConstants;
import com.pte.notification.domain.ProcessedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.messaging.consumer.dto.StudentEnrolledEvent;
import com.pte.notification.repository.ProcessedEventRepository;
import com.pte.notification.service.NotificationDispatchService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

/**
 * {@code outbox.event.ExamSession} is shared with {@code SessionScheduled}/
 * {@code ScoringRequested}/{@code PublishRequested} — this consumer only acts
 * on {@code StudentEnrolled}, same filter-by-header pattern as every other
 * shared-topic consumer in this codebase (e.g. scoring's ScoringCommandConsumer).
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

    @KafkaListener(topics = NotificationConstants.TOPIC_SESSION_EVENTS, groupId = "notification-enrollment")
    @Transactional
    public void onSessionEvent(ConsumerRecord<String, String> record) throws IOException {
        UUID eventId = KafkaHeaders.require(record, "id");
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = KafkaHeaders.requireString(record, NotificationConstants.KAFKA_HEADER_EVENT_TYPE);
        if (NotificationConstants.INCOMING_EVENT_STUDENT_ENROLLED.equals(eventType)) {
            notifyStudent(record.value());
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
}
