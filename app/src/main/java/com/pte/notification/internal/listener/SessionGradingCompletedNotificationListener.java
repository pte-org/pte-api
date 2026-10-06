package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.InboxRecipient;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.service.InboxAppendService;
import com.pte.scoring.SessionGradingCompletedEvent;
import com.pte.tenancy.TenancyService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Comparator;

/** Maps one frozen-cohort completion transition to the tenant host inbox. */
@Component
public class SessionGradingCompletedNotificationListener {

    private final IdentityService identityService;
    private final TenancyService tenancyService;
    private final InboxAppendService appender;

    public SessionGradingCompletedNotificationListener(IdentityService identityService,
            TenancyService tenancyService, InboxAppendService appender) {
        this.identityService = identityService;
        this.tenancyService = tenancyService;
        this.appender = appender;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onCompleted(SessionGradingCompletedEvent event) {
        if (event == null || event.tenantPublicId() == null || event.sessionPublicId() == null
                || event.cohortPublicId() == null || event.cohortVersion() <= 0
                || !tenancyService.isActiveTenant(event.tenantPublicId())) {
            return;
        }
        var recipients = identityService.findActiveRoleMembers(event.tenantPublicId(), Role.HOST_ADMIN).stream()
                .filter(member -> member != null && member.userPublicId() != null
                        && event.tenantPublicId().equals(member.tenantId()))
                .sorted(Comparator.comparing(member -> member.userPublicId()))
                .map(member -> new InboxRecipient(member.userPublicId(), event.tenantPublicId()))
                .toList();
        String eventKey = "session:%s:grading-completed:%d".formatted(event.sessionPublicId(), event.cohortVersion());
        appender.append(new InboxNotificationRequested(
                InboxConstants.SCHEMA_VERSION, eventKey, InboxNotificationType.SESSION_GRADING_COMPLETED,
                InboxCategory.SESSION, InboxImportance.INFO,
                InboxConstants.SESSION_GRADING_COMPLETED_NOTIFICATION_TITLE,
                InboxConstants.SESSION_GRADING_COMPLETED_NOTIFICATION_BODY.formatted(event.sessionPublicId()),
                InboxTargetType.SESSION, event.sessionPublicId(), recipients));
    }
}
