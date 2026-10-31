package com.pte.notification.internal.listener;

import com.pte.identity.PasswordResetEmailRequestedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;

/** The reset mail lists one link per account and is sent without keeping the links in notification history. */
@ExtendWith(MockitoExtension.class)
class PasswordResetNotificationListenerTest {

    @Mock
    private NotificationDispatchService dispatchService;

    @Test
    void mailsEveryLinkInVietnameseAndEnglishThroughTheSensitiveChannel() {
        PasswordResetNotificationListener listener = new PasswordResetNotificationListener(dispatchService);
        UUID requestId = UUID.randomUUID();
        PasswordResetEmailRequestedEvent event = new PasswordResetEmailRequestedEvent(requestId, "shared@x.vn",
                List.of(new PasswordResetEmailRequestedEvent.Link("alice", "https://host.example/reset-password?token=aaa"),
                        new PasswordResetEmailRequestedEvent.Link("bob", "https://admin.example/reset-password?token=bbb")));

        listener.onPasswordResetRequested(event);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternalSensitive(
                org.mockito.ArgumentMatchers.eq(NotificationType.PASSWORD_RESET_LINK_SENT),
                org.mockito.ArgumentMatchers.eq("shared@x.vn"), isNull(),
                startsWith("password-reset:" + requestId), subject.capture(), body.capture());
        assertThat(body.getValue()).contains("alice", "https://host.example/reset-password?token=aaa",
                "bob", "https://admin.example/reset-password?token=bbb");
        assertThat(body.getValue()).contains("30");
        assertThat(subject.getValue()).isNotBlank();
        assertThat(body.getValue()).contains("Đặt lại mật khẩu").contains("Reset your password");
    }
}
