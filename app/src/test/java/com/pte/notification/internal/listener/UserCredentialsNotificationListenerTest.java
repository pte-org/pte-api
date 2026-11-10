package com.pte.notification.internal.listener;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** A temporary password goes out as a card plus a plain twin, and never into notification history. */
@ExtendWith(MockitoExtension.class)
class UserCredentialsNotificationListenerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2031-05-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private NotificationDispatchService dispatchService;

    @Test
    void mailsTheLoginDetailsAsACardAndAPlainTwin() {
        UserCredentialsNotificationListener listener = new UserCredentialsNotificationListener(dispatchService, CLOCK);
        UUID requestId = UUID.randomUUID();
        UserCredentialsEmailRequestedEvent event = new UserCredentialsEmailRequestedEvent(requestId, UUID.randomUUID(),
                UUID.randomUUID(), "tenant.student", "student@x.vn", "An Nguyen", "ABCD-2345");

        listener.onCredentialsRequested(event);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternalSensitive(eq(NotificationType.USER_CREDENTIALS_SENT),
                eq("student@x.vn"), eq(event.tenantId()), eq("user-credentials:" + requestId), subject.capture(),
                text.capture(), html.capture());
        assertThat(subject.getValue()).isNotBlank();
        assertThat(text.getValue()).contains("An Nguyen", "tenant.student", "ABCD-2345");
        assertThat(html.getValue()).contains("An Nguyen", "tenant.student", "ABCD-2345", "© 2031 PTE Prep");
    }
}
