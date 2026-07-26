package com.pte.examdelivery.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import com.pte.examdelivery.domain.ProcessedEvent;
import com.pte.examdelivery.messaging.consumer.dto.ProctorCommandEvent;
import com.pte.examdelivery.repository.ProcessedEventRepository;
import com.pte.examdelivery.service.ProctorCommandService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

/**
 * exam-delivery's first Kafka consumer (phase-10): applies proctor's
 * FORCE_SUBMIT/EXTEND_TIME commands. Idempotent, same dedup pattern as every
 * other consumer in this codebase (ADR-002) — checked before apply, saved
 * after, inside one transaction.
 */
@Component
public class ProctorCommandConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final ProctorCommandService proctorCommandService;
    private final ObjectMapper objectMapper;

    public ProctorCommandConsumer(ProcessedEventRepository processedEventRepository,
                                  ProctorCommandService proctorCommandService, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.proctorCommandService = proctorCommandService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = ExamDeliveryConstants.TOPIC_PROCTOR_COMMAND_EVENTS, groupId = "exam-delivery-proctor-command")
    @Transactional
    public void onProctorCommand(ConsumerRecord<String, String> record) throws IOException {
        UUID eventId = KafkaHeaders.require(record, "id");
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        ProctorCommandEvent event = objectMapper.readValue(record.value(), ProctorCommandEvent.class);
        applyCommand(event);

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void applyCommand(ProctorCommandEvent event) {
        if (ExamDeliveryConstants.COMMAND_TYPE_FORCE_SUBMIT.equals(event.commandType())) {
            proctorCommandService.forceSubmit(event.attemptPublicId(), event.tenantId());
        } else if (ExamDeliveryConstants.COMMAND_TYPE_EXTEND_TIME.equals(event.commandType())) {
            if (event.extraSeconds() != null) {
                proctorCommandService.extendResponseTime(event.attemptPublicId(), event.tenantId(), event.extraSeconds());
            }
        }
        // Any other/unknown commandType: ignored, not an error (forward-compat with future command types).
    }
}
