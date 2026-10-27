package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.constant.NotificationConstants;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.scoring.ScoringService;
import com.pte.session.SessionService;
import com.pte.session.dto.event.SessionCancelledEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Notifies only active proctors and examiners assigned to a cancelled session. */
@Component
public class SessionCancelledNotificationListener {

    private final SessionService sessionService;
    private final ScoringService scoringService;
    private final IdentityService identityService;
    private final NotificationDispatchService dispatchService;
    private final Clock clock;

    public SessionCancelledNotificationListener(SessionService sessionService, ScoringService scoringService,
            IdentityService identityService, NotificationDispatchService dispatchService,
            Clock clock) {
        this.sessionService = sessionService;
        this.scoringService = scoringService;
        this.identityService = identityService;
        this.dispatchService = dispatchService;
        this.clock = clock;
    }

    @TransactionalEventListener
    public void onSessionCancelled(SessionCancelledEvent event) {
        Set<UUID> staffPublicIds = new LinkedHashSet<>();
        staffPublicIds.addAll(sessionService.findAssignedProctorPublicIds(
                event.sessionPublicId(), event.tenantId()));
        staffPublicIds.addAll(scoringService.findAssignedExaminerPublicIds(
                event.tenantId(), event.sessionPublicId()));

        String sessionLabel = event.sessionName() == null || event.sessionName().isBlank()
                ? event.sessionPublicId().toString() : event.sessionName();
        String cancelledAt = event.cancelledAt() == null ? clock.instant().toString() : event.cancelledAt().toString();
        String body = NotificationConstants.SESSION_CANCELLED_BODY.formatted(sessionLabel, cancelledAt);
        for (UUID staffPublicId : staffPublicIds) {
            identityService.findActiveStaffInTenant(staffPublicId, event.tenantId())
                    .ifPresent(user -> dispatchService.dispatch(NotificationType.SESSION_CANCELLED, user,
                            event.tenantId(), NotificationConstants.SESSION_CANCELLED_SUBJECT, body,
                            NotificationConstants.SESSION_CANCELLED_DEDUPE_KEY.formatted(
                                    event.sessionPublicId(), user.getPublicId())));
        }
    }
}
