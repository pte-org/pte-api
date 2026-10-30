package com.pte.notification.internal.listener;

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
import com.pte.session.dto.event.ExamPreviewReadyEvent;
import com.pte.tenancy.TenancyService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/** Maps the first generated exam snapshot to the generating host's inbox. */
@Component
public class ExamPreviewReadyNotificationListener {

    private final IdentityService identityService;
    private final TenancyService tenancyService;
    private final InboxAppendService appender;

    public ExamPreviewReadyNotificationListener(IdentityService identityService,
            TenancyService tenancyService, InboxAppendService appender) {
        this.identityService = identityService;
        this.tenancyService = tenancyService;
        this.appender = appender;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onPreviewReady(ExamPreviewReadyEvent event) {
        if (event == null || event.sessionPublicId() == null || event.tenantPublicId() == null
                || event.snapshotPublicId() == null || event.actorUserPublicId() == null
                || blank(event.sessionCode()) || blank(event.sessionName())
                || event.occurredAt() == null || !tenancyService.isActiveTenant(event.tenantPublicId())
                || !isActiveHost(event)) {
            return;
        }
        String eventKey = "session:%s:preview-ready:%s:%s".formatted(
                event.sessionPublicId(), event.snapshotPublicId(), event.actorUserPublicId());
        appender.append(new InboxNotificationRequested(
                InboxConstants.SCHEMA_VERSION,
                eventKey,
                InboxNotificationType.SESSION_PREVIEW_READY,
                InboxCategory.SESSION,
                InboxImportance.INFO,
                InboxConstants.SESSION_PREVIEW_READY_NOTIFICATION_TITLE,
                InboxConstants.SESSION_PREVIEW_READY_NOTIFICATION_BODY.formatted(
                        event.sessionName(), event.sessionCode()),
                InboxTargetType.SESSION,
                event.sessionPublicId(),
                List.of(new InboxRecipient(event.actorUserPublicId(), event.tenantPublicId()))));
    }

    private boolean isActiveHost(ExamPreviewReadyEvent event) {
        return identityService.findActiveRoleMembers(event.tenantPublicId(), Role.HOST_ADMIN).stream()
                .filter(member -> member != null && event.tenantPublicId().equals(member.tenantId()))
                .map(IdentityRoleMember::userPublicId)
                .anyMatch(event.actorUserPublicId()::equals);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
