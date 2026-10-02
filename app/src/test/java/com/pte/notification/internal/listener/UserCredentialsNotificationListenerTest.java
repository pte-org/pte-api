package com.pte.notification.internal.listener;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserCredentialsNotificationListenerTest {

    @Mock
    private NotificationDispatchService dispatchService;

    @Test
    void credentialsEmail_containsTemporaryPassword_butUsesSensitiveDispatch() {
        UserCredentialsNotificationListener listener = new UserCredentialsNotificationListener(dispatchService);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        listener.onCredentialsRequested(new UserCredentialsEmailRequestedEvent(
                requestId, userId, tenantId, "school.student", "student@example.com", "Student One", "Abcd-2345"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternalSensitive(
                eq(NotificationType.USER_CREDENTIALS_SENT), eq("student@example.com"), eq(tenantId),
                contains(requestId.toString()), contains("temporary"), body.capture());
        assertThat(body.getValue()).contains("Username: school.student");
        assertThat(body.getValue()).contains("Temporary password: Abcd-2345");
    }
}
