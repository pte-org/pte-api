package com.pte.notification.internal.messaging.consumer;

import com.pte.notification.domain.NotificationLog;
import com.pte.notification.domain.enums.NotificationStatus;
import com.pte.notification.internal.messaging.job.EmailJob;
import com.pte.notification.internal.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailWorkerTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private JavaMailSender mailSender;

    private EmailWorker service;

    @BeforeEach
    void setUp() {
        service = new EmailWorker(notificationLogRepository, mailSender);
    }

    @Test
    void onEmailJob_logFound_pendingStatus_sendEmailAndMarkSent() {
        UUID logPublicId = UUID.randomUUID();
        String recipientEmail = "student@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        EmailJob job = new EmailJob(logPublicId, recipientEmail, subject, body);

        NotificationLog log = new NotificationLog();
        log.setPublicId(logPublicId);
        log.setRecipientEmail(recipientEmail);
        log.setSubject(subject);
        log.setBody(body);
        log.setStatus(NotificationStatus.PENDING);

        when(notificationLogRepository.findByPublicId(logPublicId)).thenReturn(Optional.of(log));

        service.onEmailJob(job);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.getTo()).isEqualTo(new String[]{recipientEmail});
        assertThat(sentMessage.getSubject()).isEqualTo(subject);
        assertThat(sentMessage.getText()).isEqualTo(body);

        assertThat(log.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(log.getSentAt()).isNotNull();
        verify(notificationLogRepository).save(log);
    }

    @Test
    void onEmailJob_logNotFound_noOp() {
        UUID logPublicId = UUID.randomUUID();
        EmailJob job = new EmailJob(logPublicId, "student@example.com", "Subject", "Body");

        when(notificationLogRepository.findByPublicId(logPublicId)).thenReturn(Optional.empty());

        service.onEmailJob(job);

        verify(mailSender, never()).send(isA(SimpleMailMessage.class));
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    void onEmailJob_logAlreadySent_noOp() {
        UUID logPublicId = UUID.randomUUID();
        EmailJob job = new EmailJob(logPublicId, "student@example.com", "Subject", "Body");

        NotificationLog log = new NotificationLog();
        log.setPublicId(logPublicId);
        log.setStatus(NotificationStatus.SENT);

        when(notificationLogRepository.findByPublicId(logPublicId)).thenReturn(Optional.of(log));

        service.onEmailJob(job);

        verify(mailSender, never()).send(isA(SimpleMailMessage.class));
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    void onEmailJob_logAlreadyFailed_noOp() {
        UUID logPublicId = UUID.randomUUID();
        EmailJob job = new EmailJob(logPublicId, "student@example.com", "Subject", "Body");

        NotificationLog log = new NotificationLog();
        log.setPublicId(logPublicId);
        log.setStatus(NotificationStatus.FAILED);

        when(notificationLogRepository.findByPublicId(logPublicId)).thenReturn(Optional.of(log));

        service.onEmailJob(job);

        verify(mailSender, never()).send(isA(SimpleMailMessage.class));
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    void onEmailJob_mailSenderThrows_propagatesException_doesNotMarkSent() {
        UUID logPublicId = UUID.randomUUID();
        String recipientEmail = "student@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        EmailJob job = new EmailJob(logPublicId, recipientEmail, subject, body);

        NotificationLog log = new NotificationLog();
        log.setPublicId(logPublicId);
        log.setRecipientEmail(recipientEmail);
        log.setStatus(NotificationStatus.PENDING);

        when(notificationLogRepository.findByPublicId(logPublicId)).thenReturn(Optional.of(log));

        org.mockito.Mockito.doThrow(new MailSendException("SMTP connection failed"))
                .when(mailSender).send(isA(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.onEmailJob(job))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email send failed");

        assertThat(log.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(log.getSentAt()).isNull();
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    void onDeadLettered_logFound_pendingStatus_marksFailed() {
        UUID logPublicId = UUID.randomUUID();
        EmailJob job = new EmailJob(logPublicId, "student@example.com", "Subject", "Body");

        NotificationLog log = new NotificationLog();
        log.setPublicId(logPublicId);
        log.setStatus(NotificationStatus.PENDING);

        when(notificationLogRepository.findByPublicId(logPublicId)).thenReturn(Optional.of(log));

        service.onDeadLettered(job);

        assertThat(log.getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(notificationLogRepository).save(log);
    }

    @Test
    void onDeadLettered_logAlreadySent_leavesUntouched() {
        UUID logPublicId = UUID.randomUUID();
        EmailJob job = new EmailJob(logPublicId, "student@example.com", "Subject", "Body");

        NotificationLog log = new NotificationLog();
        log.setPublicId(logPublicId);
        log.setStatus(NotificationStatus.SENT);

        when(notificationLogRepository.findByPublicId(logPublicId)).thenReturn(Optional.of(log));

        service.onDeadLettered(job);

        assertThat(log.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    void onDeadLettered_logNotFound_noOp() {
        UUID logPublicId = UUID.randomUUID();
        EmailJob job = new EmailJob(logPublicId, "student@example.com", "Subject", "Body");

        when(notificationLogRepository.findByPublicId(logPublicId)).thenReturn(Optional.empty());

        service.onDeadLettered(job);

        verify(notificationLogRepository, never()).save(any());
    }
}
