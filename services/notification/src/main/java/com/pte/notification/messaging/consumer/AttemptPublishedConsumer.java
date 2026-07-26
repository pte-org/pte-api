package com.pte.notification.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.notification.constant.NotificationConstants;
import com.pte.notification.domain.ProcessedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.messaging.consumer.dto.AttemptPublishedEvent;
import com.pte.notification.repository.ProcessedEventRepository;
import com.pte.notification.service.NotificationDispatchService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

/** Notifies the student that their report is now visible (reporting's host-gated publish, ADR-002). */
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

    @KafkaListener(topics = NotificationConstants.TOPIC_ATTEMPT_REPORT_EVENTS, groupId = "notification-attempt-published")
    @Transactional
    public void onAttemptReportEvent(ConsumerRecord<String, String> record) throws IOException {
        UUID eventId = KafkaHeaders.require(record, "id");
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = KafkaHeaders.requireString(record, NotificationConstants.KAFKA_HEADER_EVENT_TYPE);
        if (NotificationConstants.INCOMING_EVENT_ATTEMPT_PUBLISHED.equals(eventType)) {
            notifyStudent(record.value());
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
}
