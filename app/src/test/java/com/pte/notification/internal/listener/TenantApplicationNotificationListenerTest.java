package com.pte.notification.internal.listener;

import com.pte.billing.TenantApplicationApprovedEvent;
import com.pte.billing.TenantApplicationRejectedEvent;
import com.pte.billing.TenantApplicationSubmittedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.config.NotificationProperties;
import com.pte.notification.internal.service.NotificationDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TenantApplicationNotificationListenerTest {

    @Mock
    private NotificationDispatchService dispatchService;

    private TenantApplicationNotificationListener listener;

    @BeforeEach
    void setUp() {
        NotificationProperties properties = new NotificationProperties();
        properties.setApplicationResponseSlaHours(48);
        listener = new TenantApplicationNotificationListener(dispatchService, properties);
    }

    @Test
    void submitted_sendsOneProfessionalConfirmationWithConfiguredSla() {
        UUID applicationId = UUID.randomUUID();

        listener.onApplicationSubmitted(new TenantApplicationSubmittedEvent(
                applicationId, "Acme School", "acme", "contact@acme.example"));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternal(
                org.mockito.ArgumentMatchers.eq(NotificationType.TENANT_APPLICATION_SUBMITTED),
                org.mockito.ArgumentMatchers.eq("contact@acme.example"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.contains(":submitted"),
                subject.capture(), body.capture());
        assertThat(subject.getValue()).contains("Application received");
        assertThat(body.getValue()).contains("within 48 hours");
        assertThat(body.getValue()).doesNotContain(applicationId.toString());
        assertThat(body.getValue()).doesNotContain("Application ID");
    }

    @Test
    void approved_sendsLoginCredentialsWithoutApplicationId() {
        UUID applicationId = UUID.randomUUID();
        listener.onApplicationApproved(new TenantApplicationApprovedEvent(
                applicationId, UUID.randomUUID(), "Acme School", "acme", "contact@acme.example",
                "contact@acme.example", "Gener4ted!"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternalSensitive(
                org.mockito.ArgumentMatchers.eq(NotificationType.TENANT_APPLICATION_APPROVED),
                org.mockito.ArgumentMatchers.eq("contact@acme.example"),
                org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.contains(":approved"),
                org.mockito.ArgumentMatchers.contains("approved"), body.capture());
        assertThat(body.getValue()).contains("Tenant code: acme");
        assertThat(body.getValue()).contains("Username: contact@acme.example");
        assertThat(body.getValue()).contains("Temporary password: Gener4ted!");
        assertThat(body.getValue()).doesNotContain(applicationId.toString());
    }

    @Test
    void rejected_sendsReasonToApplicant() {
        UUID applicationId = UUID.randomUUID();
        listener.onApplicationRejected(new TenantApplicationRejectedEvent(
                applicationId, "Acme School", "acme", "contact@acme.example", "Missing documents"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternal(
                org.mockito.ArgumentMatchers.eq(NotificationType.TENANT_APPLICATION_REJECTED),
                org.mockito.ArgumentMatchers.eq("contact@acme.example"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.contains(":rejected"),
                org.mockito.ArgumentMatchers.contains("Update"), body.capture());
        assertThat(body.getValue()).contains("Reason: Missing documents");
        assertThat(body.getValue()).doesNotContain(applicationId.toString());
        assertThat(body.getValue()).doesNotContain("Application ID");
    }
}
