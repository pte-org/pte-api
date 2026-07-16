package com.aptis.modules.examdelivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.domain.ExamAttemptStatus;
import com.aptis.modules.examdelivery.repository.ExamAttemptRepository;
import com.aptis.modules.examoperations.domain.Exam;
import com.aptis.modules.examoperations.repository.ExamRepository;
import com.aptis.modules.proctor.domain.ProctorActionType;
import com.aptis.modules.proctor.dto.BroadcastAnnouncement;
import com.aptis.modules.proctor.service.ProctorActionAuditService;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProctorBroadcastService Tests")
class ProctorBroadcastServiceTest {

    @Mock
    private ExamRepository examRepository;

    @Mock
    private ExamAttemptRepository examAttemptRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ProctorActionAuditService auditService;

    private ProctorBroadcastService service;
    private JwtPrincipal proctorPrincipal;

    @BeforeEach
    void setUp() {
        service = new ProctorBroadcastService(examRepository, examAttemptRepository, messagingTemplate, auditService);
        proctorPrincipal = new JwtPrincipal(1L, "PROCTOR", "PROCTOR", 100L);
    }

    // ====================
    // broadcast Tests
    // ====================

    @Test
    @DisplayName("broadcast: exam found, proctor assigned → sends message and audits")
    void broadcast_proctorAssigned_sendAndAudit() {
        Long examId = 100L;
        long recipientCount = 5L;
        String message = "Time remaining: 10 minutes";

        Exam exam = mock(Exam.class);
        when(exam.getProctorId()).thenReturn(proctorPrincipal.userId());
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        when(examAttemptRepository.countByExamIdAndStatusIn(eq(examId), any()))
                .thenReturn(recipientCount);

        int result = service.broadcast(examId, message, proctorPrincipal);

        assertThat(result).isEqualTo((int) recipientCount);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BroadcastAnnouncement> announcementCaptor = ArgumentCaptor.forClass(BroadcastAnnouncement.class);
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), announcementCaptor.capture());

        assertThat(destinationCaptor.getValue()).isEqualTo("/topic/exam/" + examId + "/announcements");
        assertThat(announcementCaptor.getValue().message()).isEqualTo(message);

        verify(auditService).record(eq(proctorPrincipal.userId()), eq(null),
                eq(ProctorActionType.BROADCAST_ANNOUNCEMENT), any());
    }

    @Test
    @DisplayName("broadcast: exam found, different proctor assigned → throws ACCESS_DENIED")
    void broadcast_differentProctor_throwsAccessDenied() {
        Long examId = 100L;
        Long differentProctorId = 999L;
        String message = "Test message";

        Exam exam = mock(Exam.class);
        when(exam.getProctorId()).thenReturn(differentProctorId);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.broadcast(examId, message, proctorPrincipal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(BroadcastAnnouncement.class));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    @DisplayName("broadcast: exam found, no proctor assigned (null) → throws ACCESS_DENIED")
    void broadcast_noProctorAssigned_throwsAccessDenied() {
        Long examId = 100L;
        String message = "Test message";

        Exam exam = mock(Exam.class);
        when(exam.getProctorId()).thenReturn(null);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.broadcast(examId, message, proctorPrincipal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(BroadcastAnnouncement.class));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    @DisplayName("broadcast: exam not found → throws RESOURCE_NOT_FOUND")
    void broadcast_examNotFound_throwsResourceNotFound() {
        Long examId = 999L;
        String message = "Test message";

        when(examRepository.findById(examId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.broadcast(examId, message, proctorPrincipal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(BroadcastAnnouncement.class));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    @DisplayName("broadcast: zero recipients → still sends message")
    void broadcast_zeroRecipients_stillSends() {
        Long examId = 100L;
        long recipientCount = 0L;
        String message = "Exam has ended";

        Exam exam = mock(Exam.class);
        when(exam.getProctorId()).thenReturn(proctorPrincipal.userId());
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        when(examAttemptRepository.countByExamIdAndStatusIn(eq(examId), any()))
                .thenReturn(recipientCount);

        int result = service.broadcast(examId, message, proctorPrincipal);

        assertThat(result).isEqualTo(0);
        verify(messagingTemplate).convertAndSend(anyString(), any(BroadcastAnnouncement.class));
        verify(auditService).record(any(), any(), any(), any());
    }

    @Test
    @DisplayName("broadcast: targetAttemptId is null for broadcast (one-to-many)")
    void broadcast_targetAttemptIdNull() {
        Long examId = 100L;
        String message = "Test message";

        Exam exam = mock(Exam.class);
        when(exam.getProctorId()).thenReturn(proctorPrincipal.userId());
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        when(examAttemptRepository.countByExamIdAndStatusIn(eq(examId), any()))
                .thenReturn(3L);

        service.broadcast(examId, message, proctorPrincipal);

        ArgumentCaptor<Long> targetAttemptIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(auditService).record(any(), targetAttemptIdCaptor.capture(), any(), any());

        assertThat(targetAttemptIdCaptor.getValue()).isNull();
    }
}
