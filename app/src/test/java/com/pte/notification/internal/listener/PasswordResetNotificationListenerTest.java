package com.pte.notification.internal.listener;

import com.pte.identity.PasswordResetEmailRequestedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;

/** The reset mail is a card with a button per account, sent without keeping the links in notification history. */
@ExtendWith(MockitoExtension.class)
class PasswordResetNotificationListenerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2031-05-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private NotificationDispatchService dispatchService;

    @Test
    void mailsACardAndItsPlainTwinThroughTheSensitiveChannel() {
        PasswordResetNotificationListener listener = new PasswordResetNotificationListener(dispatchService, CLOCK);
        UUID requestId = UUID.randomUUID();
        PasswordResetEmailRequestedEvent event = new PasswordResetEmailRequestedEvent(requestId, "shared@x.vn",
                List.of(new PasswordResetEmailRequestedEvent.Link("alice", "https://host.example/reset-password?token=aaa"),
                        new PasswordResetEmailRequestedEvent.Link("bob", "https://admin.example/reset-password?token=bbb")),
                45);

        listener.onPasswordResetRequested(event);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternalSensitive(eq(NotificationType.PASSWORD_RESET_LINK_SENT),
                eq("shared@x.vn"), isNull(), startsWith("password-reset:" + requestId), subject.capture(),
                text.capture(), html.capture());
        assertThat(subject.getValue()).contains("Reset your password");
        assertThat(text.getValue()).contains("alice", "https://host.example/reset-password?token=aaa", "bob",
                "https://admin.example/reset-password?token=bbb", "45");
        assertThat(html.getValue()).contains("href=\"https://host.example/reset-password?token=aaa\"",
                "href=\"https://admin.example/reset-password?token=bbb\"", "45 minutes", "© 2031 PTE Prep");
    }
}
