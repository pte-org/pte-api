package com.pte.notification.internal.listener;

import com.pte.billing.TenantApplicationSubmittedEvent;
import com.pte.identity.IdentityRoleMember;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Comparator;
import java.util.List;

/** Appends the admin inbox item before the application transaction commits. */
@Component
public class TenantApplicationInboxNotificationListener {
    private final IdentityService identity;
    private final InboxAppendService appender;

    public TenantApplicationInboxNotificationListener(IdentityService identity, InboxAppendService appender) {
        this.identity = identity;
        this.appender = appender;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onApplicationSubmitted(TenantApplicationSubmittedEvent event) {
        List<InboxRecipient> recipients = identity.findActiveRoleMembers(Role.PLATFORM_ADMIN).stream()
                .filter(member -> member.userPublicId() != null && member.tenantId() == null)
                .map(member -> new InboxRecipient(member.userPublicId(), null))
                .sorted(Comparator.comparing(InboxRecipient::userPublicId))
                .toList();
        appender.append(new InboxNotificationRequested(
                InboxConstants.SCHEMA_VERSION,
                "application:%s:submitted".formatted(event.applicationPublicId()),
                InboxNotificationType.APPLICATION_SUBMITTED,
                InboxCategory.APPLICATION,
                InboxImportance.INFO,
                InboxConstants.APPLICATION_NOTIFICATION_TITLE,
                InboxConstants.APPLICATION_NOTIFICATION_BODY.formatted(safeOrganizationName(event.organizationName())),
                InboxTargetType.APPLICATION,
                event.applicationPublicId(),
                recipients));
    }

    private String safeOrganizationName(String organizationName) {
        if (organizationName == null || organizationName.isBlank()) {
            return "organization";
        }
        String trimmed = organizationName.trim();
        return trimmed.length() <= InboxConstants.APPLICATION_NAME_LIMIT
                ? trimmed : trimmed.substring(0, InboxConstants.APPLICATION_NAME_LIMIT);
    }
}
