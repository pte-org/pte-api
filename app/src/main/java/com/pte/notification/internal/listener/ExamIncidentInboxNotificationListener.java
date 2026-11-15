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
import com.pte.proctoring.dto.event.ExamIncidentReportedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Comparator;
import java.util.List;

/** Tells the tenant's host admins that a proctor reported an incident; the reporter is not told about their own. */
@Component
public class ExamIncidentInboxNotificationListener {

    private final IdentityService identityService;
    private final InboxAppendService appender;

    public ExamIncidentInboxNotificationListener(IdentityService identityService, InboxAppendService appender) {
        this.identityService = identityService;
        this.appender = appender;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onReported(ExamIncidentReportedEvent event) {
        if (event == null || event.incidentPublicId() == null || event.tenantId() == null
                || event.sessionPublicId() == null || event.title() == null || event.title().isBlank()) {
            return;
        }
        List<InboxRecipient> recipients = identityService.findActiveRoleMembers(event.tenantId(), Role.HOST_ADMIN)
                .stream()
                .filter(member -> member != null && member.userPublicId() != null
                        && event.tenantId().equals(member.tenantId())
                        && !member.userPublicId().equals(event.reporterPublicId()))
                .sorted(Comparator.comparing(member -> member.userPublicId()))
                .map(member -> new InboxRecipient(member.userPublicId(), event.tenantId()))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        String severity = event.severity() == null ? "" : event.severity();
        String body = InboxConstants.EXAM_INCIDENT_REPORTED_NOTIFICATION_BODY.formatted(
                severity.toLowerCase(java.util.Locale.ROOT), event.title().strip());
        appender.append(new InboxNotificationRequested(
                InboxConstants.SCHEMA_VERSION, "incident:%s:reported".formatted(event.incidentPublicId()),
                InboxNotificationType.EXAM_INCIDENT_REPORTED, InboxCategory.SESSION,
                InboxConstants.IMPORTANT_INCIDENT_SEVERITIES.contains(severity)
                        ? InboxImportance.IMPORTANT : InboxImportance.INFO,
                InboxConstants.EXAM_INCIDENT_REPORTED_NOTIFICATION_TITLE,
                body.length() > InboxConstants.BODY_LIMIT ? body.substring(0, InboxConstants.BODY_LIMIT) : body,
                InboxTargetType.SESSION, event.sessionPublicId(), recipients));
    }
}
