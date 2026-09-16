package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.session.dto.event.SessionCancelledEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Notifies enrolled students when a scheduled session is cancelled. */
@Component
public class SessionCancelledNotificationListener {

    private final IdentityService identityService;
    private final NotificationDispatchService dispatchService;

    public SessionCancelledNotificationListener(IdentityService identityService,
            NotificationDispatchService dispatchService) {
        this.identityService = identityService;
        this.dispatchService = dispatchService;
    }

    @TransactionalEventListener
    public void onSessionCancelled(SessionCancelledEvent event) {
        for (var studentPublicId : event.studentPublicIds()) {
            dispatchService.dispatch(NotificationType.SESSION_CANCELLED,
                    identityService.findById(studentPublicId).orElse(null), event.tenantId(),
                    "Exam session cancelled",
                    "Exam session " + event.sessionPublicId() + " has been cancelled.");
        }
    }
}
