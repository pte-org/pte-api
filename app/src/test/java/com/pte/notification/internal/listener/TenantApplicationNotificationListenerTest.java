package com.pte.notification.internal.listener;

import com.pte.billing.BillingSettingsService;
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
        NotificationProperties properties = new NotificationProperties();
        properties.setSupportEmail("help@pte.example");
        properties.setLoginUrl("https://tenant.example/login");
        listener = new TenantApplicationNotificationListener(dispatchService, billingSettingsService, properties, clock);
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
        assertThat(subject.getValue()).isEqualTo("[PTE Prep] We've received your application - Acme School");
        assertThat(text.getValue()).contains("within 48 hours").contains("Requested tenant code: acme")
                .contains("Write to help@pte.example");
        assertThat(html.getValue()).contains("<!DOCTYPE html>").contains("48 hours").contains("© 2026")
                .contains("Acme School").contains("mailto:help@pte.example");
        assertThat(text.getValue() + html.getValue()).doesNotContain(applicationId.toString());
    }

    @Test
    void approved_sendsLoginCredentialsCardWithoutApplicationId() {
        UUID applicationId = UUID.randomUUID();
        listener.onApplicationApproved(new TenantApplicationApprovedEvent(
                applicationId, UUID.randomUUID(), "Acme School", "acme", "contact@acme.example",
                "acme.k7m2p9qx", "Gener4ted!"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternalSensitive(eq(NotificationType.TENANT_APPLICATION_APPROVED),
                eq("contact@acme.example"), org.mockito.ArgumentMatchers.any(UUID.class), contains(":approved"),
                contains("approved"), text.capture(), html.capture());
        assertThat(text.getValue()).contains("Tenant code: acme").contains("Username: acme.k7m2p9qx")
                .contains("Temporary password: Gener4ted!").contains("Sign in: https://tenant.example/login")
                .contains("help@pte.example");
        assertThat(html.getValue()).contains("<!DOCTYPE html>").contains("Gener4ted!").contains("Acme School")
                .contains("href=\"https://tenant.example/login\"").contains("Approved")
                .contains("Dear Acme School team").contains("mailto:help@pte.example");
        assertThat(text.getValue() + html.getValue()).doesNotContain(applicationId.toString());
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
