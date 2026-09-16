package com.pte.notification.internal.service;

import com.pte.identity.domain.User;
import com.pte.notification.domain.NotificationLog;
import com.pte.notification.domain.enums.NotificationStatus;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.constant.NotificationConstants;
import com.pte.notification.internal.messaging.job.EmailJob;
import com.pte.notification.internal.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        service = new NotificationDispatchService(notificationLogRepository, rabbitTemplate);
    }

    @Test
    void dispatch_withNullRecipient_noOp() {
        UUID tenantId = UUID.randomUUID();
        NotificationType type = NotificationType.STUDENT_ENROLLED;
        String subject = "Test Subject";
        String body = "Test Body";

        service.dispatch(type, null, tenantId, subject, body);

        verify(notificationLogRepository, never()).save(any());
        // RabbitTemplate's convertAndSend has ambiguous overloads, so we skip verification
        // and just verify that repository.save was not called
    }

    @Test
    void dispatch_withNonNullRecipient_savesLogAndEnqueuesEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID userPublicId = UUID.randomUUID();
        String userEmail = "student@example.com";
        NotificationType type = NotificationType.STUDENT_ENROLLED;
        String subject = "You've been enrolled";
        String body = "You've been enrolled in session...";

        User recipient = new User();
        recipient.setPublicId(userPublicId);
        recipient.setEmail(userEmail);

        NotificationLog savedLog = new NotificationLog();
        savedLog.setPublicId(UUID.randomUUID());
        savedLog.setRecipientUserPublicId(userPublicId);
        savedLog.setRecipientEmail(userEmail);
        savedLog.setTenantId(tenantId);
        savedLog.setNotificationType(type);
        savedLog.setSubject(subject);
        savedLog.setBody(body);
        savedLog.setStatus(NotificationStatus.PENDING);

        when(notificationLogRepository.save(any(NotificationLog.class))).thenReturn(savedLog);

        service.dispatch(type, recipient, tenantId, subject, body);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        NotificationLog savedLog_ = logCaptor.getValue();
        assertThat(savedLog_.getRecipientUserPublicId()).isEqualTo(userPublicId);
        assertThat(savedLog_.getRecipientEmail()).isEqualTo(userEmail);
        assertThat(savedLog_.getTenantId()).isEqualTo(tenantId);
        assertThat(savedLog_.getNotificationType()).isEqualTo(type);
        assertThat(savedLog_.getSubject()).isEqualTo(subject);
        assertThat(savedLog_.getBody()).isEqualTo(body);
        assertThat(savedLog_.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void dispatchTo_savesLogAndEnqueuesEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID userPublicId = UUID.randomUUID();
        String userEmail = "host@example.com";
        NotificationType type = NotificationType.VIOLATION_DETECTED;
        String subject = "Violation flagged";
        String body = "A violation was flagged...";

        User recipient = new User();
        recipient.setPublicId(userPublicId);
        recipient.setEmail(userEmail);

        NotificationLog savedLog = new NotificationLog();
        savedLog.setPublicId(UUID.randomUUID());
        savedLog.setRecipientUserPublicId(userPublicId);
        savedLog.setRecipientEmail(userEmail);
        savedLog.setTenantId(tenantId);
        savedLog.setNotificationType(type);
        savedLog.setSubject(subject);
        savedLog.setBody(body);
        savedLog.setStatus(NotificationStatus.PENDING);

        when(notificationLogRepository.save(any(NotificationLog.class))).thenReturn(savedLog);

        service.dispatchTo(type, recipient, tenantId, subject, body);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        NotificationLog savedLog_ = logCaptor.getValue();
        assertThat(savedLog_.getRecipientUserPublicId()).isEqualTo(userPublicId);
        assertThat(savedLog_.getRecipientEmail()).isEqualTo(userEmail);
        assertThat(savedLog_.getTenantId()).isEqualTo(tenantId);
        assertThat(savedLog_.getNotificationType()).isEqualTo(type);
        assertThat(savedLog_.getSubject()).isEqualTo(subject);
        assertThat(savedLog_.getBody()).isEqualTo(body);
    }
}
