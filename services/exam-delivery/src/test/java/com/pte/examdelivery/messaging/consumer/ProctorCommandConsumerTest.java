package com.pte.examdelivery.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import com.pte.examdelivery.domain.ProcessedEvent;
import com.pte.examdelivery.messaging.consumer.dto.ProctorCommandEvent;
import com.pte.examdelivery.repository.ProcessedEventRepository;
import com.pte.examdelivery.service.ProctorCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Idempotency + command-dispatch tests for {@link ProctorCommandConsumer} —
 * exam-delivery's ordering-sensitive RabbitMQ consumer.
 */
@ExtendWith(MockitoExtension.class)
class ProctorCommandConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private ProctorCommandService proctorCommandService;
    @Mock
    private ObjectMapper objectMapper;

    private ProctorCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ProctorCommandConsumer(processedEventRepository, proctorCommandService, objectMapper);
    }

    private Message message(UUID eventId) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(eventId.toString());
        return new Message("{}".getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    void forceSubmitCommand_notYetProcessed_appliesAndMarksProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ProctorCommandEvent event = new ProctorCommandEvent(attemptPublicId, UUID.randomUUID(),
                ExamDeliveryConstants.COMMAND_TYPE_FORCE_SUBMIT, null, tenantId);
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(objectMapper.readValue(anyString(), eq(ProctorCommandEvent.class))).thenReturn(event);

        consumer.onProctorCommand(message(eventId));

        verify(proctorCommandService).forceSubmit(attemptPublicId, tenantId);
        verify(proctorCommandService, never()).extendResponseTime(any(), any(), anyInt());
        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
    }

    @Test
    void extendTimeCommand_withExtraSeconds_appliesExtension() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ProctorCommandEvent event = new ProctorCommandEvent(attemptPublicId, UUID.randomUUID(),
                ExamDeliveryConstants.COMMAND_TYPE_EXTEND_TIME, 300, tenantId);
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(objectMapper.readValue(anyString(), eq(ProctorCommandEvent.class))).thenReturn(event);

        consumer.onProctorCommand(message(eventId));

        verify(proctorCommandService).extendResponseTime(attemptPublicId, tenantId, 300);
        verify(proctorCommandService, never()).forceSubmit(any(), any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void extendTimeCommand_withNullExtraSeconds_appliesNothing_butStillMarksProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        ProctorCommandEvent event = new ProctorCommandEvent(UUID.randomUUID(), UUID.randomUUID(),
                ExamDeliveryConstants.COMMAND_TYPE_EXTEND_TIME, null, UUID.randomUUID());
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(objectMapper.readValue(anyString(), eq(ProctorCommandEvent.class))).thenReturn(event);

        consumer.onProctorCommand(message(eventId));

        verify(proctorCommandService, never()).extendResponseTime(any(), any(), anyInt());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void unknownCommandType_isIgnored_butStillMarksProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        ProctorCommandEvent event = new ProctorCommandEvent(UUID.randomUUID(), UUID.randomUUID(),
                "SOME_FUTURE_COMMAND", null, UUID.randomUUID());
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(objectMapper.readValue(anyString(), eq(ProctorCommandEvent.class))).thenReturn(event);

        consumer.onProctorCommand(message(eventId));

        verifyNoInteractions(proctorCommandService);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void alreadyProcessedEvent_isSkipped_withoutReapplyingCommand() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        consumer.onProctorCommand(message(eventId));

        verifyNoInteractions(proctorCommandService);
        verifyNoInteractions(objectMapper);
        verify(processedEventRepository, times(1)).existsById(eventId);
        verify(processedEventRepository, never()).save(any());
    }
}
