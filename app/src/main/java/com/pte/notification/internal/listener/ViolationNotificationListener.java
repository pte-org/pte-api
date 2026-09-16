package com.pte.notification.internal.listener;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.service.IdentityService;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.constant.NotificationConstants;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.proctoring.dto.event.ViolationDetectedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link ViolationDetectedEvent} carries no specific recipient (proctoring's
 * event only has tenantId/attemptPublicId/sessionPublicId) — fans out to
 * every HOST_ADMIN in that tenant instead of a single resolved student,
 * unlike the other two listeners.
 */
@Component
public class ViolationNotificationListener {

    private final IdentityService identityService;
    private final NotificationDispatchService dispatchService;

    public ViolationNotificationListener(IdentityService identityService, NotificationDispatchService dispatchService) {
        this.identityService = identityService;
        this.dispatchService = dispatchService;
    }

    @TransactionalEventListener
    public void onViolationDetected(ViolationDetectedEvent event) {
        String subject = "Violation flagged: " + event.violationType();
        String body = "A proctor flagged attempt " + event.attemptPublicId() + " in session " + event.sessionPublicId()
                + " for " + event.violationType() + (event.detail() == null ? "" : (" — " + event.detail())) + ".";

        for (User hostAdmin : identityService.findByTenantIdAndRole(event.tenantId(),
                Role.valueOf(NotificationConstants.ROLE_HOST_ADMIN))) {
            dispatchService.dispatchTo(NotificationType.VIOLATION_DETECTED, hostAdmin, event.tenantId(), subject, body);
        }
    }
}
