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
import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.dto.event.SupportTicketNoteAddedEvent;
import com.pte.support.dto.event.SupportTicketStatusChangedEvent;
import com.pte.support.dto.event.SupportTicketSubmittedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Maps support-ticket domain events to durable, recipient-scoped inbox intents. */
@Component
public class SupportTicketInboxNotificationListener {
    private final IdentityService identity;
    private final InboxAppendService appender;

    public SupportTicketInboxNotificationListener(IdentityService identity, InboxAppendService appender) {
        this.identity = identity;
        this.appender = appender;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onTicketSubmitted(SupportTicketSubmittedEvent event) {
        if (event == null || !validCommon(event.ticketPublicId(), event.tenantId(),
                event.submitterUserPublicId(), event.category())) {
            return;
        }
        List<InboxRecipient> recipients = identity.findActiveRoleMembers(Role.PLATFORM_ADMIN).stream()
                .filter(member -> member != null && member.userPublicId() != null && member.tenantId() == null)
                .map(member -> new InboxRecipient(member.userPublicId(), null))
                .sorted(Comparator.comparing(InboxRecipient::userPublicId))
                .toList();
        append("support-ticket:%s:submitted".formatted(event.ticketPublicId()),
                InboxNotificationType.SUPPORT_TICKET_SUBMITTED,
                InboxConstants.SUPPORT_TICKET_SUBMITTED_NOTIFICATION_TITLE,
                InboxConstants.SUPPORT_TICKET_SUBMITTED_NOTIFICATION_BODY.formatted(category(event.category())),
                event.ticketPublicId(), recipients);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onTicketNoteAdded(SupportTicketNoteAddedEvent event) {
        if (event == null || event.notePublicId() == null || !validCommon(event.ticketPublicId(), event.tenantId(),
                event.submitterUserPublicId(), event.category())) {
            return;
        }
        append("support-ticket:%s:note:%s".formatted(event.ticketPublicId(), event.notePublicId()),
                InboxNotificationType.SUPPORT_TICKET_NOTE_ADDED,
                InboxConstants.SUPPORT_TICKET_NOTE_ADDED_NOTIFICATION_TITLE,
                InboxConstants.SUPPORT_TICKET_NOTE_ADDED_NOTIFICATION_BODY.formatted(category(event.category())),
                event.ticketPublicId(), List.of(new InboxRecipient(event.submitterUserPublicId(), event.tenantId())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onTicketStatusChanged(SupportTicketStatusChangedEvent event) {
        if (event == null || !validCommon(event.ticketPublicId(), event.tenantId(),
                event.submitterUserPublicId(), event.category())
                || !validTransition(event.previousStatus(), event.newStatus())) {
            return;
        }
        append("support-ticket:%s:status:%s:%s".formatted(event.ticketPublicId(),
                        event.previousStatus(), event.newStatus()),
                InboxNotificationType.SUPPORT_TICKET_STATUS_CHANGED,
                InboxConstants.SUPPORT_TICKET_STATUS_CHANGED_NOTIFICATION_TITLE,
                InboxConstants.SUPPORT_TICKET_STATUS_CHANGED_NOTIFICATION_BODY.formatted(
                        category(event.category()), event.newStatus().name()),
                event.ticketPublicId(), List.of(new InboxRecipient(event.submitterUserPublicId(), event.tenantId())));
    }

    private void append(String eventKey, InboxNotificationType type, String title, String body,
            UUID targetPublicId, List<InboxRecipient> recipients) {
        appender.append(new InboxNotificationRequested(
                InboxConstants.SCHEMA_VERSION, eventKey, type, InboxCategory.SUPPORT, InboxImportance.INFO,
                title, body, InboxTargetType.SUPPORT_TICKET, targetPublicId, recipients));
    }

    private boolean validCommon(UUID ticketPublicId, UUID tenantId,
            UUID submitterUserPublicId, TicketCategory ticketCategory) {
        return ticketPublicId != null && tenantId != null && submitterUserPublicId != null && ticketCategory != null;
    }

    private boolean validTransition(TicketStatus previousStatus, TicketStatus newStatus) {
        return (previousStatus == TicketStatus.OPEN && newStatus == TicketStatus.IN_PROGRESS)
                || (previousStatus == TicketStatus.IN_PROGRESS && newStatus == TicketStatus.RESOLVED);
    }

    private String category(TicketCategory ticketCategory) {
        return ticketCategory.name();
    }
}
