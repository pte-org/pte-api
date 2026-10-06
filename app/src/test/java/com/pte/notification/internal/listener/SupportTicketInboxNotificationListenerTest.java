package com.pte.notification.internal.listener;

import com.pte.identity.IdentityRoleMember;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.InboxRecipient;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.service.InboxAppendService;
import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.dto.event.SupportTicketNoteAddedEvent;
import com.pte.support.dto.event.SupportTicketStatusChangedEvent;
import com.pte.support.dto.event.SupportTicketSubmittedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketInboxNotificationListenerTest {
    private static final UUID ADMIN = UUID.fromString("10000000-0000-0000-0000-000000000021");
    private static final UUID SUBMITTER = UUID.fromString("10000000-0000-0000-0000-000000000022");
    private static final UUID TENANT = UUID.fromString("20000000-0000-0000-0000-000000000021");
    private static final UUID TICKET = UUID.fromString("30000000-0000-0000-0000-000000000021");
    private static final UUID NOTE = UUID.fromString("40000000-0000-0000-0000-000000000021");

    @Mock
    private IdentityService identity;

    @Mock
    private InboxAppendService appender;

    private SupportTicketInboxNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new SupportTicketInboxNotificationListener(identity, appender);
    }

    @Test
    void submittedFansOutOnlyToPlatformAdmins() {
        when(identity.findActiveRoleMembers(Role.PLATFORM_ADMIN)).thenReturn(List.of(
                new IdentityRoleMember(ADMIN, null),
                new IdentityRoleMember(UUID.randomUUID(), TENANT),
                new IdentityRoleMember(null, null)));

        listener.onTicketSubmitted(new SupportTicketSubmittedEvent(TICKET, TENANT, SUBMITTER, TicketCategory.BUG));

        InboxNotificationRequested request = capturedRequest();
        assertThat(request.eventKey()).isEqualTo("support-ticket:" + TICKET + ":submitted");
        assertThat(request.type()).isEqualTo(InboxNotificationType.SUPPORT_TICKET_SUBMITTED);
        assertThat(request.category()).isEqualTo(InboxCategory.SUPPORT);
        assertThat(request.targetType()).isEqualTo(InboxTargetType.SUPPORT_TICKET);
        assertThat(request.recipients()).containsExactly(new InboxRecipient(ADMIN, null));
    }

    @Test
    void noteTargetsOnlyTheTicketSubmitterAndDoesNotCopyNoteText() {
        listener.onTicketNoteAdded(new SupportTicketNoteAddedEvent(
                TICKET, TENANT, SUBMITTER, NOTE, TicketCategory.GENERAL_FEEDBACK));

        InboxNotificationRequested request = capturedRequest();
        assertThat(request.eventKey()).isEqualTo("support-ticket:" + TICKET + ":note:" + NOTE);
        assertThat(request.type()).isEqualTo(InboxNotificationType.SUPPORT_TICKET_NOTE_ADDED);
        assertThat(request.recipients()).containsExactly(new InboxRecipient(SUBMITTER, TENANT));
        assertThat(request.body()).doesNotContain("note");
    }

    @Test
    void statusChangeTargetsSubmitterAndIncludesNewStatus() {
        listener.onTicketStatusChanged(new SupportTicketStatusChangedEvent(
                TICKET, TENANT, SUBMITTER, TicketCategory.CONTENT_COMPLAINT,
                TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED));

        InboxNotificationRequested request = capturedRequest();
        assertThat(request.eventKey()).isEqualTo("support-ticket:" + TICKET + ":status:IN_PROGRESS:RESOLVED");
        assertThat(request.type()).isEqualTo(InboxNotificationType.SUPPORT_TICKET_STATUS_CHANGED);
        assertThat(request.body()).contains("RESOLVED");
        assertThat(request.recipients()).containsExactly(new InboxRecipient(SUBMITTER, TENANT));
    }

    private InboxNotificationRequested capturedRequest() {
        ArgumentCaptor<InboxNotificationRequested> captor = ArgumentCaptor.forClass(InboxNotificationRequested.class);
        verify(appender).append(captor.capture());
        return captor.getValue();
    }
}
