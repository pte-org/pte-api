package com.pte.notification.internal.listener;

import com.pte.billing.TenantApplicationApprovedEvent;
import com.pte.billing.TenantApplicationRejectedEvent;
import com.pte.billing.TenantApplicationSubmittedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.config.NotificationProperties;
import com.pte.notification.internal.service.NotificationDispatchService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/** Sends the three customer-facing lifecycle emails for tenant applications. */
@Component
public class TenantApplicationNotificationListener {

    private final NotificationDispatchService dispatchService;
    private final NotificationProperties notificationProperties;

    public TenantApplicationNotificationListener(NotificationDispatchService dispatchService,
            NotificationProperties notificationProperties) {
        this.dispatchService = dispatchService;
        this.notificationProperties = notificationProperties;
    }

    @TransactionalEventListener
    public void onApplicationSubmitted(TenantApplicationSubmittedEvent event) {
        String subject = "PTE Prep - Application received";
        String body = """
                Hello,

                Thank you for submitting your organization application to PTE Prep.

                Our platform team will review your application and send an update within %d hours.

                Regards,
                PTE Prep Platform Team
                """.formatted(notificationProperties.getApplicationResponseSlaHours());

        dispatchService.dispatchExternal(NotificationType.TENANT_APPLICATION_SUBMITTED, event.contactEmail(), null,
                dedupeKey(event.applicationPublicId(), "submitted"), subject, body);
    }

    @TransactionalEventListener
    public void onApplicationApproved(TenantApplicationApprovedEvent event) {
        String subject = "PTE Prep - Your application was approved";
        String body = """
                Hello,

                We are pleased to let you know that your organization application has been approved.

                Organization: %s
                Tenant code: %s
                Username: %s
                Temporary password: %s

                Please change the temporary password after your first sign-in.

                Regards,
                PTE Prep Platform Team
                """.formatted(event.organizationName(), event.tenantCode(), event.hostAdminUsername(),
                event.hostAdminPassword());

        dispatchService.dispatchExternalSensitive(NotificationType.TENANT_APPLICATION_APPROVED, event.contactEmail(),
                event.tenantPublicId(), dedupeKey(event.applicationPublicId(), "approved"), subject, body);
    }

    @TransactionalEventListener
    public void onApplicationRejected(TenantApplicationRejectedEvent event) {
        String subject = "PTE Prep - Update on your application";
        String body = """
                Hello,

                Thank you for your interest in PTE Prep. After reviewing your organization application, we are unable
                to approve it at this time.

                Reason: %s

                Please address the reason above and submit a new application if you would like us to review it again.

                Regards,
                PTE Prep Platform Team
                """.formatted(event.rejectReason());

        dispatchService.dispatchExternal(NotificationType.TENANT_APPLICATION_REJECTED, event.contactEmail(), null,
                dedupeKey(event.applicationPublicId(), "rejected"), subject, body);
    }

    private String dedupeKey(UUID applicationPublicId, String transition) {
        return "tenant-application:%s:%s".formatted(applicationPublicId, transition);
    }
}
