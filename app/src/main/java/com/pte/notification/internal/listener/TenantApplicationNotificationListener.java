package com.pte.notification.internal.listener;

import com.pte.billing.BillingSettingsService;
import com.pte.billing.TenantApplicationApprovedEvent;
import com.pte.billing.TenantApplicationRejectedEvent;
import com.pte.billing.TenantApplicationSubmittedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.notification.internal.template.BrandedEmailTemplate;
import com.pte.notification.internal.template.RenderedEmail;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Year;
import java.util.UUID;

/** Sends the three customer-facing lifecycle emails for tenant applications as cards with a plain-text twin. */
@Component
public class TenantApplicationNotificationListener {

    private final NotificationDispatchService dispatchService;
    private final BillingSettingsService billingSettingsService;
    private final Clock clock;

    public TenantApplicationNotificationListener(NotificationDispatchService dispatchService,
            BillingSettingsService billingSettingsService, Clock clock) {
        this.dispatchService = dispatchService;
        this.billingSettingsService = billingSettingsService;
        this.clock = clock;
    }

    @TransactionalEventListener
    public void onApplicationSubmitted(TenantApplicationSubmittedEvent event) {
        RenderedEmail email = BrandedEmailTemplate.applicationReceived(
                billingSettingsService.getTenantApplicationResponseSlaHours(), currentYear());

        dispatchService.dispatchExternal(NotificationType.TENANT_APPLICATION_SUBMITTED, event.contactEmail(), null,
                dedupeKey(event.applicationPublicId(), "submitted"), email.subject(), email.text(), email.html());
    }

    /** Carries generated credentials only for a contact who had no account yet; otherwise none are issued. */
    @TransactionalEventListener
    public void onApplicationApproved(TenantApplicationApprovedEvent event) {
        RenderedEmail email = BrandedEmailTemplate.applicationApproved(event.organizationName(), event.tenantCode(),
                event.hostAdminUsername(), event.hostAdminPassword(), currentYear());

        dispatchService.dispatchExternalSensitive(NotificationType.TENANT_APPLICATION_APPROVED, event.contactEmail(),
                event.tenantPublicId(), dedupeKey(event.applicationPublicId(), "approved"), email.subject(),
                email.text(), email.html());
    }

    @TransactionalEventListener
    public void onApplicationRejected(TenantApplicationRejectedEvent event) {
        RenderedEmail email = BrandedEmailTemplate.applicationRejected(event.rejectReason(), currentYear());

        dispatchService.dispatchExternal(NotificationType.TENANT_APPLICATION_REJECTED, event.contactEmail(), null,
                dedupeKey(event.applicationPublicId(), "rejected"), email.subject(), email.text(), email.html());
    }

    private int currentYear() {
        return Year.now(clock).getValue();
    }

    private String dedupeKey(UUID applicationPublicId, String transition) {
        return "tenant-application:%s:%s".formatted(applicationPublicId, transition);
    }
}
