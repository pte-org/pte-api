package com.pte.notification.internal.service;

import com.pte.notification.domain.NotificationLog;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A sensitive HTML mail is queued with both bodies, while the notification history keeps neither. */
@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceHtmlTest {

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
    void sensitiveHtml_queuesBothBodiesAndKeepsHistoryRedacted() {
        UUID logId = UUID.randomUUID();
        when(notificationLogRepository.findByDedupeKey("k")).thenReturn(Optional.empty());
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(call -> {
            NotificationLog saved = call.getArgument(0);
            saved.setPublicId(logId);
            return saved;
        });

        service.dispatchExternalSensitive(NotificationType.PASSWORD_RESET_LINK_SENT, "a@b.vn", null, "k", "subject",
                "plain twin", "<p>card</p>");

        ArgumentCaptor<NotificationLog> history = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(history.capture());
        assertThat(history.getValue().getBody()).isEqualTo(NotificationConstants.SENSITIVE_EMAIL_BODY_REDACTED);
        ArgumentCaptor<EmailJob> job = ArgumentCaptor.forClass(EmailJob.class);
        verify(rabbitTemplate).convertAndSend(eq(NotificationConstants.EMAIL_EXCHANGE),
                eq(NotificationConstants.EMAIL_ROUTING_KEY), job.capture());
        assertThat(job.getValue().notificationLogPublicId()).isEqualTo(logId);
        assertThat(job.getValue().body()).isEqualTo("plain twin");
        assertThat(job.getValue().htmlBody()).isEqualTo("<p>card</p>");
    }

    @Test
    void sensitiveHtml_withTheSameDedupeKey_sendsNothingTwice() {
        when(notificationLogRepository.findByDedupeKey("k")).thenReturn(Optional.of(new NotificationLog()));

        service.dispatchExternalSensitive(NotificationType.PASSWORD_RESET_LINK_SENT, "a@b.vn", null, "k", "s", "t", "h");

        verify(notificationLogRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void theOlderPlainOverload_stillQueuesAPlainJob() {
        UUID logId = UUID.randomUUID();
        when(notificationLogRepository.findByDedupeKey("k")).thenReturn(Optional.empty());
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(call -> {
            NotificationLog saved = call.getArgument(0);
            saved.setPublicId(logId);
            return saved;
        });

        service.dispatchExternalSensitive(NotificationType.USER_CREDENTIALS_SENT, "a@b.vn", null, "k", "s", "plain");

        ArgumentCaptor<EmailJob> job = ArgumentCaptor.forClass(EmailJob.class);
        verify(rabbitTemplate).convertAndSend(eq(NotificationConstants.EMAIL_EXCHANGE),
                eq(NotificationConstants.EMAIL_ROUTING_KEY), job.capture());
        assertThat(job.getValue().htmlBody()).isNull();
    }
}
