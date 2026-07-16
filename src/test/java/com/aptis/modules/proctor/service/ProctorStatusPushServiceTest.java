package com.aptis.modules.proctor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.aptis.modules.examoperations.domain.Exam;
import com.aptis.modules.examoperations.repository.ExamRepository;
import com.aptis.modules.iam.repository.ProctorRepository;
import com.aptis.modules.proctor.dto.ProctorStatusMessage;

import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProctorStatusPushService Tests")
class ProctorStatusPushServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private ProctorRepository proctorRepository;

    private ProctorStatusPushService service;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        service = new ProctorStatusPushService(messagingTemplate, examRepository, proctorRepository);
        now = OffsetDateTime.now();
    }

    // ==================== pushStatus Tests ====================

    @Test
    @DisplayName("pushStatus: exam with assigned proctor sends message")
    void pushStatus_withAssignedProctor_sendMessage() {
        // Arrange
        Long examId = 100L;
        Long proctorId = 1L;
        Long attemptId = 200L;
        String studentId = "STU-001";
        String status = "IN_PROGRESS";
        OffsetDateTime lastHeartbeat = now.minusSeconds(10);
        OffsetDateTime submittedAt = null;

        Exam exam = mock(Exam.class);
        when(exam.getId()).thenReturn(examId);
        when(exam.getProctorId()).thenReturn(proctorId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(proctorRepository.existsById(proctorId)).thenReturn(true);

        // Act
        service.pushStatus(examId, attemptId, studentId, status, lastHeartbeat, submittedAt);

        // Assert
        ArgumentCaptor<ProctorStatusMessage> messageCaptor = ArgumentCaptor.forClass(ProctorStatusMessage.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(proctorId.toString()),
                eq("/" + examId + "/queue/attempt-status"),
                messageCaptor.capture());

        ProctorStatusMessage sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.attemptId()).isEqualTo(attemptId);
        assertThat(sentMessage.examId()).isEqualTo(examId);
        assertThat(sentMessage.studentId()).isEqualTo(studentId);
        assertThat(sentMessage.status()).isEqualTo(status);
        assertThat(sentMessage.lastHeartbeatAt()).isEqualTo(lastHeartbeat);
        assertThat(sentMessage.submittedAt()).isNull();
        assertThat(sentMessage.eventTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("pushStatus: exam without proctor does not send message")
    void pushStatus_withoutProctor_noMessage() {
        // Arrange
        Long examId = 100L;
        Long attemptId = 200L;
        String studentId = "STU-001";

        Exam exam = mock(Exam.class);
        when(exam.getProctorId()).thenReturn(null);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        // Act
        service.pushStatus(examId, attemptId, studentId, "IN_PROGRESS", now, null);

        // Assert
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    @DisplayName("pushStatus: exam not found does not send message")
    void pushStatus_examNotFound_noMessage() {
        // Arrange
        Long examId = 999L;
        Long attemptId = 200L;
        String studentId = "STU-001";

        when(examRepository.findById(examId)).thenReturn(Optional.empty());

        // Act
        service.pushStatus(examId, attemptId, studentId, "IN_PROGRESS", now, null);

        // Assert
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    @DisplayName("pushStatus: proctor not found does not send message")
    void pushStatus_proctorNotFound_noMessage() {
        // Arrange
        Long examId = 100L;
        Long proctorId = 999L;
        Long attemptId = 200L;
        String studentId = "STU-001";

        Exam exam = mock(Exam.class);
        when(exam.getProctorId()).thenReturn(proctorId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(proctorRepository.existsById(proctorId)).thenReturn(false);

        // Act
        service.pushStatus(examId, attemptId, studentId, "IN_PROGRESS", now, null);

        // Assert
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    @DisplayName("pushStatus: destination is formatted as /examId/queue/attempt-status")
    void pushStatus_destinationFormat_correct() {
        // Arrange
        Long examId = 500L;
        Long proctorId = 1L;
        Long attemptId = 200L;

        Exam exam = mock(Exam.class);
        when(exam.getId()).thenReturn(examId);
        when(exam.getProctorId()).thenReturn(proctorId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(proctorRepository.existsById(proctorId)).thenReturn(true);

        // Act
        service.pushStatus(examId, attemptId, "STU-001", "SUBMITTED", now, now);

        // Assert
        verify(messagingTemplate).convertAndSendToUser(
                eq(proctorId.toString()),
                eq("/500/queue/attempt-status"),
                any(ProctorStatusMessage.class));
    }

    @Test
    @DisplayName("pushStatus: user key is proctorId as string (userId)")
    void pushStatus_userKey_isProctorIdString() {
        // Arrange
        Long examId = 100L;
        Long proctorId = 12345L;
        Long attemptId = 200L;

        Exam exam = mock(Exam.class);
        when(exam.getId()).thenReturn(examId);
        when(exam.getProctorId()).thenReturn(proctorId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(proctorRepository.existsById(proctorId)).thenReturn(true);

        // Act
        service.pushStatus(examId, attemptId, "STU-001", "IN_PROGRESS", now, null);

        // Assert
        verify(messagingTemplate).convertAndSendToUser(
                eq("12345"),
                any(),
                any());
    }

    @Test
    @DisplayName("pushStatus: messaging exception is caught and logged (best-effort)")
    void pushStatus_messagingException_swallowed() {
        // Arrange
        Long examId = 100L;
        Long proctorId = 1L;
        Long attemptId = 200L;

        Exam exam = mock(Exam.class);
        when(exam.getId()).thenReturn(examId);
        when(exam.getProctorId()).thenReturn(proctorId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(proctorRepository.existsById(proctorId)).thenReturn(true);

        doThrow(new RuntimeException("Network error"))
                .when(messagingTemplate).convertAndSendToUser(any(), any(), any());

        // Act — should not throw
        service.pushStatus(examId, attemptId, "STU-001", "IN_PROGRESS", now, null);

        // Assert — exception was caught
        verify(messagingTemplate).convertAndSendToUser(any(), any(), any());
    }

    // ==================== ProctorStatusMessage Content Tests ====================

    @Test
    @DisplayName("ProctorStatusMessage: contains all required fields")
    void message_containsAllFields() {
        // Arrange
        Long examId = 100L;
        Long proctorId = 1L;
        Long attemptId = 200L;
        String studentId = "STU-001";
        String status = "SECTION_SWITCH";
        OffsetDateTime lastHeartbeat = now.minusSeconds(5);
        OffsetDateTime submitted = now.minusSeconds(2);

        Exam exam = mock(Exam.class);
        when(exam.getId()).thenReturn(examId);
        when(exam.getProctorId()).thenReturn(proctorId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(proctorRepository.existsById(proctorId)).thenReturn(true);

        ArgumentCaptor<ProctorStatusMessage> captor = ArgumentCaptor.forClass(ProctorStatusMessage.class);

        // Act
        service.pushStatus(examId, attemptId, studentId, status, lastHeartbeat, submitted);

        // Assert
        verify(messagingTemplate).convertAndSendToUser(any(), any(), captor.capture());
        ProctorStatusMessage msg = captor.getValue();

        assertThat(msg.attemptId()).isEqualTo(attemptId);
        assertThat(msg.examId()).isEqualTo(examId);
        assertThat(msg.studentId()).isEqualTo(studentId);
        assertThat(msg.status()).isEqualTo(status);
        assertThat(msg.lastHeartbeatAt()).isEqualTo(lastHeartbeat);
        assertThat(msg.submittedAt()).isEqualTo(submitted);
        assertThat(msg.eventTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("ProctorStatusMessage: timestamps are set")
    void message_timestampsArePresent() {
        // Arrange
        Long examId = 100L;
        Long proctorId = 1L;

        Exam exam = mock(Exam.class);
        when(exam.getId()).thenReturn(examId);
        when(exam.getProctorId()).thenReturn(proctorId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(proctorRepository.existsById(proctorId)).thenReturn(true);

        ArgumentCaptor<ProctorStatusMessage> captor = ArgumentCaptor.forClass(ProctorStatusMessage.class);

        OffsetDateTime beforeCall = OffsetDateTime.now();

        // Act
        service.pushStatus(examId, 200L, "STU-001", "SUBMITTED", now, now);

        OffsetDateTime afterCall = OffsetDateTime.now();

        // Assert
        verify(messagingTemplate).convertAndSendToUser(any(), any(), captor.capture());
        ProctorStatusMessage msg = captor.getValue();

        assertThat(msg.eventTimestamp()).isNotNull();
        assertThat(msg.eventTimestamp()).isBetween(beforeCall, afterCall);
    }
}
