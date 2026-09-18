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
        String subject = "PTE Hub - Application received";
        String body = """
                Hello,

                Thank you for submitting your organization application to PTE Hub.

                Application ID: %s
                Organization: %s
                Requested tenant code: %s

                Our platform team will review your application and send an update within %d hours.
                Please keep this email and your application ID for reference.

                Regards,
                PTE Hub Platform Team
                """.formatted(event.applicationPublicId(), event.organizationName(), event.requestedCode(),
                notificationProperties.getApplicationResponseSlaHours());

        dispatchService.dispatchExternal(NotificationType.TENANT_APPLICATION_SUBMITTED, event.contactEmail(), null,
                dedupeKey(event.applicationPublicId(), "submitted"), subject, body);
    }

    @TransactionalEventListener
    public void onApplicationApproved(TenantApplicationApprovedEvent event) {
        String subject = "PTE Hub - Your application was approved";
        String body = """
                Hello,

                We are pleased to let you know that your organization application has been approved.

                Organization: %s
                Tenant code: %s
                Login username: %s

                A platform administrator will provide the one-time password through a separate secure channel.
                Please change that password after your first sign-in.

                Regards,
                PTE Hub Platform Team
                """.formatted(event.organizationName(), event.tenantCode(), event.hostAdminUsername());

        dispatchService.dispatchExternal(NotificationType.TENANT_APPLICATION_APPROVED, event.contactEmail(),
                event.tenantPublicId(), dedupeKey(event.applicationPublicId(), "approved"), subject, body);
    }

    @TransactionalEventListener
    public void onApplicationRejected(TenantApplicationRejectedEvent event) {
        String subject = "PTE Hub - Update on your application";
        String body = """
                Hello,

                Thank you for your interest in PTE Hub. After reviewing your organization application, we are unable
                to approve it at this time.

                Application ID: %s
                Organization: %s
                Requested tenant code: %s
                Reason: %s

                Please address the reason above and submit a new application if you would like us to review it again.

                Regards,
                PTE Hub Platform Team
                """.formatted(event.applicationPublicId(), event.organizationName(), event.requestedCode(),
                event.rejectReason());

        dispatchService.dispatchExternal(NotificationType.TENANT_APPLICATION_REJECTED, event.contactEmail(), null,
                dedupeKey(event.applicationPublicId(), "rejected"), subject, body);
    }

    private String dedupeKey(UUID applicationPublicId, String transition) {
        return "tenant-application:%s:%s".formatted(applicationPublicId, transition);
    }
}
