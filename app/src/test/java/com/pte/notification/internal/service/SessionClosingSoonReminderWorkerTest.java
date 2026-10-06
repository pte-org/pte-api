package com.pte.notification.internal.service;

import com.pte.identity.IdentityRoleMember;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.internal.repository.InboxDeliveryStore;
import com.pte.session.SessionService;
import com.pte.session.dto.response.ClosingSoonSessionView;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionClosingSoonReminderWorkerTest {

    @Mock
    private SessionService sessionService;
    @Mock
    private IdentityService identityService;
    @Mock
    private TenancyService tenancyService;
    @Mock
    private InboxAppendService appender;
    @Mock
    private InboxDeliveryStore deliveryStore;

    @Test
    void appendsOneReminderOnlyAfterLockedSessionIsRevalidated() {
        Instant now = Instant.parse("2026-10-03T10:00:00Z");
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        ClosingSoonSessionView candidate = new ClosingSoonSessionView(sessionId, tenantId, "Mock exam",
                Instant.parse("2026-10-03T09:00:00Z"), Instant.parse("2026-10-03T10:10:00Z"));
        when(sessionService.findDueClosingSoonSessions(now, now.plusSeconds(900))).thenReturn(List.of(candidate));
        when(sessionService.lockDueClosingSoonSession(sessionId, now, now.plusSeconds(900)))
                .thenReturn(Optional.of(candidate));
        when(tenancyService.isActiveTenant(tenantId)).thenReturn(true);
        when(identityService.findActiveRoleMembers(eq(tenantId), eq(Role.HOST_ADMIN)))
                .thenReturn(List.of(new IdentityRoleMember(hostId, tenantId)));

        SessionClosingSoonReminderWorker worker = worker(now);

        assertThat(worker.deliverDueReminders()).isEqualTo(1);

        ArgumentCaptor<InboxNotificationRequested> captor =
                ArgumentCaptor.forClass(InboxNotificationRequested.class);
        verify(appender).append(captor.capture());
        InboxNotificationRequested request = captor.getValue();
        assertThat(request.type()).isEqualTo(InboxNotificationType.SESSION_CLOSING_SOON);
        assertThat(request.targetPublicId()).isEqualTo(sessionId);
        assertThat(request.eventKey()).contains(sessionId.toString()).contains("900");
        verify(deliveryStore).suppressPendingSessionReminders(eq(sessionId), eq(request.eventKey()), eq(now));
    }

    @Test
    void staleCandidateIsDiscardedWhenLockedRevalidationFails() {
        Instant now = Instant.parse("2026-10-03T10:00:00Z");
        UUID sessionId = UUID.randomUUID();
        ClosingSoonSessionView candidate = new ClosingSoonSessionView(sessionId, UUID.randomUUID(), "Mock exam",
                now.minusSeconds(3600), now.plusSeconds(600));
        when(sessionService.findDueClosingSoonSessions(now, now.plusSeconds(900))).thenReturn(List.of(candidate));
        when(sessionService.lockDueClosingSoonSession(sessionId, now, now.plusSeconds(900)))
                .thenReturn(Optional.empty());

        assertThat(worker(now).deliverDueReminders()).isZero();
    }

    private SessionClosingSoonReminderWorker worker(Instant now) {
        return new SessionClosingSoonReminderWorker(sessionService, identityService, tenancyService, appender,
                deliveryStore, Clock.fixed(now, ZoneOffset.UTC), 900);
    }
}
