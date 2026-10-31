package com.pte.notification.internal.listener;

import com.pte.billing.BillingSettingsService;
import com.pte.billing.TenantApplicationApprovedEvent;
import com.pte.billing.TenantApplicationRejectedEvent;
import com.pte.billing.TenantApplicationSubmittedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantApplicationNotificationListenerTest {

    @Mock
    private NotificationDispatchService dispatchService;

    @Mock
    private BillingSettingsService billingSettingsService;

    private TenantApplicationNotificationListener listener;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
        listener = new TenantApplicationNotificationListener(dispatchService, billingSettingsService, clock);
    }

    @Test
    void submitted_sendsOneCardWithConfiguredSla() {
        when(billingSettingsService.getTenantApplicationResponseSlaHours()).thenReturn(48);
        UUID applicationId = UUID.randomUUID();

        listener.onApplicationSubmitted(new TenantApplicationSubmittedEvent(
                applicationId, "Acme School", "acme", "contact@acme.example"));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternal(eq(NotificationType.TENANT_APPLICATION_SUBMITTED),
                eq("contact@acme.example"), isNull(), contains(":submitted"), subject.capture(), text.capture(),
                html.capture());
        assertThat(subject.getValue()).contains("Application received");
        assertThat(text.getValue()).contains("within 48 hours");
        assertThat(html.getValue()).contains("<!DOCTYPE html>").contains("48 hours").contains("© 2026");
        assertThat(text.getValue() + html.getValue()).doesNotContain(applicationId.toString());
    }

    @Test
    void approved_sendsLoginCredentialsCardWithoutApplicationId() {
        UUID applicationId = UUID.randomUUID();
        listener.onApplicationApproved(new TenantApplicationApprovedEvent(
                applicationId, UUID.randomUUID(), "Acme School", "acme", "contact@acme.example",
                "contact@acme.example", "Gener4ted!"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternalSensitive(eq(NotificationType.TENANT_APPLICATION_APPROVED),
                eq("contact@acme.example"), org.mockito.ArgumentMatchers.any(UUID.class), contains(":approved"),
                contains("approved"), text.capture(), html.capture());
        assertThat(text.getValue()).contains("Tenant code: acme").contains("Username: contact@acme.example")
                .contains("Temporary password: Gener4ted!");
        assertThat(html.getValue()).contains("<!DOCTYPE html>").contains("Gener4ted!").contains("Acme School");
        assertThat(text.getValue() + html.getValue()).doesNotContain(applicationId.toString());
    }

    @Test
    void approved_forExistingAccountIssuesNoPassword() {
        listener.onApplicationApproved(new TenantApplicationApprovedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Acme School", "acme", "contact@acme.example",
                "contact@acme.example", null));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternalSensitive(eq(NotificationType.TENANT_APPLICATION_APPROVED),
                eq("contact@acme.example"), org.mockito.ArgumentMatchers.any(UUID.class), contains(":approved"),
                contains("approved"), text.capture(), html.capture());
        assertThat(text.getValue()).doesNotContain("Temporary password: ").contains("existing account");
        assertThat(html.getValue()).doesNotContain("Temporary password").contains("existing account");
    }

    @Test
    void rejected_sendsEscapedReasonToApplicant() {
        UUID applicationId = UUID.randomUUID();
        listener.onApplicationRejected(new TenantApplicationRejectedEvent(
                applicationId, "Acme School", "acme", "contact@acme.example", "Missing <b>documents</b>"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternal(eq(NotificationType.TENANT_APPLICATION_REJECTED),
                eq("contact@acme.example"), isNull(), contains(":rejected"), contains("Update"), text.capture(),
                html.capture());
        assertThat(text.getValue()).contains("Reason: Missing <b>documents</b>");
        assertThat(html.getValue()).contains("Missing &lt;b&gt;documents&lt;/b&gt;").doesNotContain("<b>documents");
        assertThat(text.getValue() + html.getValue()).doesNotContain(applicationId.toString());
    }
}
