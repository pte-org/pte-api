package com.pte.notification.internal.listener;

import com.pte.identity.IdentityRoleMember;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.internal.service.InboxAppendService;
import com.pte.scoring.SessionGradingCompletedEvent;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionGradingCompletedNotificationListenerTest {

    @Mock private IdentityService identityService;
    @Mock private TenancyService tenancyService;
    @Mock private InboxAppendService appender;

    @Test
    void completionNotifiesOnlyActiveTenantHostsOncePerCohortVersion() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(tenancyService.isActiveTenant(tenantId)).thenReturn(true);
        when(identityService.findActiveRoleMembers(eq(tenantId), eq(Role.HOST_ADMIN)))
                .thenReturn(List.of(new IdentityRoleMember(hostId, tenantId)));

        var listener = new SessionGradingCompletedNotificationListener(identityService, tenancyService, appender);
        listener.onCompleted(new SessionGradingCompletedEvent(tenantId, sessionId, cohortId, 1, 40));

        ArgumentCaptor<InboxNotificationRequested> captor =
                ArgumentCaptor.forClass(InboxNotificationRequested.class);
        verify(appender).append(captor.capture());
        InboxNotificationRequested request = captor.getValue();
        assertThat(request.type()).isEqualTo(InboxNotificationType.SESSION_GRADING_COMPLETED);
        assertThat(request.eventKey()).isEqualTo("session:%s:grading-completed:1".formatted(sessionId));
        assertThat(request.recipients()).extracting("userPublicId").containsExactly(hostId);
    }

    @Test
    void inactiveTenantDoesNotCreateCompletionNotice() {
        UUID tenantId = UUID.randomUUID();
        when(tenancyService.isActiveTenant(tenantId)).thenReturn(false);
        var listener = new SessionGradingCompletedNotificationListener(identityService, tenancyService, appender);

        listener.onCompleted(new SessionGradingCompletedEvent(tenantId, UUID.randomUUID(), UUID.randomUUID(), 1, 1));

        verify(appender, never()).append(any());
    }
}
