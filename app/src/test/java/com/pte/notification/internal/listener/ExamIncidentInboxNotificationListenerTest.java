package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.identity.IdentityRoleMember;
import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.service.InboxAppendService;
import com.pte.proctoring.dto.event.ExamIncidentReportedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamIncidentInboxNotificationListenerTest {

    private final IdentityService identity = mock(IdentityService.class);
    private final InboxAppendService appender = mock(InboxAppendService.class);
    private final ExamIncidentInboxNotificationListener listener =
            new ExamIncidentInboxNotificationListener(identity, appender);
    private final UUID tenant = UUID.randomUUID();
    private final UUID session = UUID.randomUUID();

    @Test
    void tellsTheTenantsHostAdminsButNotTheReporter() {
        UUID host = UUID.randomUUID();
        UUID reportingHost = UUID.randomUUID();
        when(identity.findActiveRoleMembers(tenant, Role.HOST_ADMIN)).thenReturn(List.of(
                member(host, tenant), member(reportingHost, tenant), member(UUID.randomUUID(), UUID.randomUUID())));

        listener.onReported(new ExamIncidentReportedEvent(UUID.randomUUID(), tenant, session, reportingHost,
                "Student left the room", "HIGH"));

        ArgumentCaptor<InboxNotificationRequested> sent = ArgumentCaptor.forClass(InboxNotificationRequested.class);
        verify(appender).append(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(InboxNotificationType.EXAM_INCIDENT_REPORTED);
        assertThat(sent.getValue().importance()).isEqualTo(InboxImportance.IMPORTANT);
        assertThat(sent.getValue().targetType()).isEqualTo(InboxTargetType.SESSION);
        assertThat(sent.getValue().targetPublicId()).isEqualTo(session);
        assertThat(sent.getValue().body()).contains("Student left the room");
        assertThat(sent.getValue().recipients()).extracting(recipient -> recipient.userPublicId())
                .containsExactly(host);
    }

    @Test
    void sendsNothingWhenNoOtherHostAdminExists() {
        UUID reporter = UUID.randomUUID();
        when(identity.findActiveRoleMembers(tenant, Role.HOST_ADMIN)).thenReturn(List.of(member(reporter, tenant)));

        listener.onReported(new ExamIncidentReportedEvent(UUID.randomUUID(), tenant, session, reporter,
                "Noise", "LOW"));

        verify(appender, never()).append(any());
    }

    private static IdentityRoleMember member(UUID user, UUID tenantId) {
        return new IdentityRoleMember(user, tenantId);
    }
}
