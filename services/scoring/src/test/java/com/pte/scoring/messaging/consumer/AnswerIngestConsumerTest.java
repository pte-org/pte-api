package com.pte.scoring.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ProcessedEvent;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.messaging.consumer.dto.AnswerSubmittedEvent;
import com.pte.scoring.repository.ProcessedEventRepository;
import com.pte.scoring.repository.ScoringAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Idempotency + dedup tests for {@link AnswerIngestConsumer} — scoring's
 * ordering-sensitive RabbitMQ consumer accumulating exam-delivery's
 * {@code AnswerSubmitted} events.
 */
@ExtendWith(MockitoExtension.class)
class AnswerIngestConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private ScoringAnswerRepository scoringAnswerRepository;
    @Mock
    private ObjectMapper objectMapper;

    private AnswerIngestConsumer consumer;

    @BeforeEach
    void setUp() {
        // Minimal stub so TransactionTemplate's REQUIRES_NEW wrapping actually
        // invokes the real callback (which calls the real repository mock) —
        // no real transaction semantics needed for these unit tests.
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        consumer = new AnswerIngestConsumer(processedEventRepository, scoringAnswerRepository, objectMapper,
                transactionManager);
    }

    private Message message(UUID eventId, String eventType) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(eventId.toString());
        if (eventType != null) {
            properties.getHeaders().put(ScoringConstants.EVENT_TYPE_HEADER, eventType);
        }
        return new Message("{}".getBytes(StandardCharsets.UTF_8), properties);
    }

    private AnswerSubmittedEvent sampleEvent() {
        return new AnswerSubmittedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), false, "READING_FILL_BLANKS", "payload",
                "correct", "options");
    }

    @Test
    void answerSubmittedEvent_notYetProcessed_ingestsAndMarksProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        AnswerSubmittedEvent event = sampleEvent();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(objectMapper.readValue(anyString(), eq(AnswerSubmittedEvent.class))).thenReturn(event);

        consumer.onAttemptEvent(message(eventId, ScoringConstants.INCOMING_EVENT_ANSWER_SUBMITTED));

        ArgumentCaptor<ScoringAnswer> captor = ArgumentCaptor.forClass(ScoringAnswer.class);
        verify(scoringAnswerRepository).save(captor.capture());
        assertThat(captor.getValue().getAnswerPublicId()).isEqualTo(event.answerPublicId());
        assertThat(captor.getValue().getAttemptPublicId()).isEqualTo(event.attemptPublicId());
        assertThat(captor.getValue().getTaskType()).isEqualTo(event.taskType());

        ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(processedCaptor.capture());
        assertThat(processedCaptor.getValue().getEventId()).isEqualTo(eventId);
    }

    @Test
    void redeliveredEvent_alreadyProcessed_doesNotSaveAgain() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        consumer.onAttemptEvent(message(eventId, ScoringConstants.INCOMING_EVENT_ANSWER_SUBMITTED));

        verifyNoInteractions(scoringAnswerRepository);
        verifyNoInteractions(objectMapper);
        verify(processedEventRepository, times(1)).existsById(eventId);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void otherEventType_onSameQueue_isIgnored_butStillMarksProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.onAttemptEvent(message(eventId, "AttemptSubmitted"));

        verifyNoInteractions(scoringAnswerRepository);
        verifyNoInteractions(objectMapper);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void missingEventTypeHeader_throwsIllegalStateException() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        assertThatThrownBy(() -> consumer.onAttemptEvent(message(eventId, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ScoringConstants.EVENT_TYPE_HEADER);
    }

    @Test
    void duplicateInsert_atDbLevel_isSwallowed_andEventStillMarkedProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        AnswerSubmittedEvent event = sampleEvent();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(objectMapper.readValue(anyString(), eq(AnswerSubmittedEvent.class))).thenReturn(event);
        when(scoringAnswerRepository.save(any(ScoringAnswer.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate answer_public_id"));

        consumer.onAttemptEvent(message(eventId, ScoringConstants.INCOMING_EVENT_ANSWER_SUBMITTED));

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }
}
