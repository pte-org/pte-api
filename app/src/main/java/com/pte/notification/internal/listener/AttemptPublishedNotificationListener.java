package com.pte.notification.internal.listener;

import com.pte.identity.internal.service.IdentityService;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.reporting.dto.event.AttemptPublishedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notifies a student their report is now visible, once reporting's host-gated
 * publish command marks it so.
 */
@Component
public class AttemptPublishedNotificationListener {

    private final IdentityService identityService;
    private final NotificationDispatchService dispatchService;

    public AttemptPublishedNotificationListener(IdentityService identityService,
            NotificationDispatchService dispatchService) {
        this.identityService = identityService;
        this.dispatchService = dispatchService;
    }

    @TransactionalEventListener
    public void onAttemptPublished(AttemptPublishedEvent event) {
        dispatchService.dispatch(NotificationType.ATTEMPT_PUBLISHED,
                identityService.findById(event.studentPublicId()).orElse(null),
                event.tenantId(), "Your exam report is ready",
                "Your report for attempt " + event.attemptPublicId() + " is now available. Log in to view your score.");
    }
}
