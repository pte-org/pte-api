package com.pte.examdelivery.messaging.consumer;

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
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private JsonMapper jsonMapper;

    private ProctorCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ProctorCommandConsumer(processedEventRepository, proctorCommandService, jsonMapper);
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
                ExamDeliveryConstants.COMMAND_TYPE_FORCE_SUBMIT, tenantId);
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(jsonMapper.readValue(anyString(), eq(ProctorCommandEvent.class))).thenReturn(event);

        consumer.onProctorCommand(message(eventId));

        verify(proctorCommandService).forceSubmit(attemptPublicId, tenantId);
        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
    }

    /**
     * EXTEND_TIME itself was removed (client-side-exam-timer Phase 5 — see
     * {@code ProctorCommandEvent}'s doc comment), so a lingering "EXTEND_TIME"
     * string is now indistinguishable from any other unrecognized commandType:
     * an in-flight message from a not-yet-upgraded proctor instance during a
     * rolling deploy must be ignored, not an error, same as
     * {@code unknownCommandType_isIgnored_butStillMarksProcessed} below.
     */
    @Test
    void formerExtendTimeCommand_isIgnored_butStillMarksProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        ProctorCommandEvent event = new ProctorCommandEvent(UUID.randomUUID(), UUID.randomUUID(),
                "EXTEND_TIME", UUID.randomUUID());
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(jsonMapper.readValue(anyString(), eq(ProctorCommandEvent.class))).thenReturn(event);

        consumer.onProctorCommand(message(eventId));

        verifyNoInteractions(proctorCommandService);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void unknownCommandType_isIgnored_butStillMarksProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        ProctorCommandEvent event = new ProctorCommandEvent(UUID.randomUUID(), UUID.randomUUID(),
                "SOME_FUTURE_COMMAND", UUID.randomUUID());
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(jsonMapper.readValue(anyString(), eq(ProctorCommandEvent.class))).thenReturn(event);

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
        verifyNoInteractions(jsonMapper);
        verify(processedEventRepository, times(1)).existsById(eventId);
        verify(processedEventRepository, never()).save(any());
    }
}
