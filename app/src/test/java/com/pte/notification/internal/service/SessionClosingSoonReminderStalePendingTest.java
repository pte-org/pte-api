package com.pte.notification.internal.service;

import com.pte.identity.IdentityService;
import com.pte.notification.internal.repository.InboxDeliveryStore;
import com.pte.session.SessionService;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionClosingSoonReminderStalePendingTest {

    @Mock private SessionService sessionService;
    @Mock private IdentityService identityService;
    @Mock private TenancyService tenancyService;
    @Mock private InboxAppendService appender;
    @Mock private InboxDeliveryStore deliveryStore;

    @Test
    void suppressesPendingReminderWhenReschedulingMovesSessionOutsideCurrentWindow() {
        Instant now = Instant.parse("2026-10-03T10:00:00Z");
        UUID sessionId = UUID.randomUUID();
        when(deliveryStore.findPendingSessionReminderTargets()).thenReturn(List.of(sessionId));
        when(sessionService.lockDueClosingSoonSession(sessionId, now, now.plusSeconds(900)))
                .thenReturn(Optional.empty());
        when(sessionService.findDueClosingSoonSessions(now, now.plusSeconds(900))).thenReturn(List.of());

        SessionClosingSoonReminderWorker worker = new SessionClosingSoonReminderWorker(sessionService,
                identityService, tenancyService, appender, deliveryStore, Clock.fixed(now, ZoneOffset.UTC), 900);

        assertThat(worker.deliverDueReminders()).isZero();

        verify(deliveryStore).suppressPendingSessionReminders(eq(sessionId), eq(null), eq(now));
    }
}
