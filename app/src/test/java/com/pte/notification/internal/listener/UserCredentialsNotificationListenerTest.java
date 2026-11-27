package com.pte.notification.internal.listener;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.config.NotificationProperties;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A temporary password goes out as a card plus a plain twin, and never into notification history. */
@ExtendWith(MockitoExtension.class)
class UserCredentialsNotificationListenerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2031-05-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private NotificationDispatchService dispatchService;

    @Mock
    private TenancyService tenancyService;

    @Test
    void mailsStudentLoginDetailsWithPracticeSignInUrl() {
        NotificationProperties properties = new NotificationProperties();
        properties.setTenantWebUrl("http://localhost:3000");
        properties.setPracticeWebUrl("http://localhost:3014");
        UUID tenantId = UUID.randomUUID();
        when(tenancyService.findOrganizationName(tenantId)).thenReturn(Optional.of("Ninh Lê Organization 2"));

        UserCredentialsNotificationListener listener = new UserCredentialsNotificationListener(
                dispatchService, properties, tenancyService, CLOCK);
        UUID requestId = UUID.randomUUID();
        UserCredentialsEmailRequestedEvent event = new UserCredentialsEmailRequestedEvent(requestId, UUID.randomUUID(),
                tenantId, "tenant.student", "student@x.vn", "An Nguyen", "ABCD-2345", "Student", true);

        listener.onCredentialsRequested(event);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternalSensitive(eq(NotificationType.USER_CREDENTIALS_SENT),
                eq("student@x.vn"), eq(event.tenantId()), eq("user-credentials:" + requestId), subject.capture(),
                text.capture(), html.capture());
        assertThat(subject.getValue()).contains("Welcome to PTE Prep");
        assertThat(text.getValue()).contains("An Nguyen", "Ninh Lê Organization 2", "Student", "tenant.student",
                "ABCD-2345", "http://localhost:3014/sign-in");
        assertThat(html.getValue()).contains("Welcome to PTE Prep", "An Nguyen", "Ninh Lê Organization 2", "Student",
                "ABCD-2345", "Sign in to PTE Prep Practice", "http://localhost:3014/sign-in", "© 2031 PTE Prep");
    }

    @Test
    void mailsStaffLoginDetailsWithTenantLoginUrl() {
        NotificationProperties properties = new NotificationProperties();
        properties.setTenantWebUrl("https://tenant.example.com");
        properties.setPracticeWebUrl("https://practice.example.com");
        UUID tenantId = UUID.randomUUID();
        when(tenancyService.findOrganizationName(tenantId)).thenReturn(Optional.of("Ninh Lê Organization 2"));

        UserCredentialsNotificationListener listener = new UserCredentialsNotificationListener(
                dispatchService, properties, tenancyService, CLOCK);
        UUID requestId = UUID.randomUUID();
        UserCredentialsEmailRequestedEvent event = new UserCredentialsEmailRequestedEvent(requestId, UUID.randomUUID(),
                tenantId, "tenant.proctor", "proctor@x.vn", "Binh Le", "EFGH-6789", "Proctor", true);

        listener.onCredentialsRequested(event);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternalSensitive(eq(NotificationType.USER_CREDENTIALS_SENT),
                eq("proctor@x.vn"), eq(event.tenantId()), eq("user-credentials:" + requestId),
                org.mockito.ArgumentMatchers.anyString(), text.capture(), html.capture());
        assertThat(text.getValue()).contains("https://tenant.example.com/login");
        assertThat(html.getValue()).contains("https://tenant.example.com/login");
    }
}
