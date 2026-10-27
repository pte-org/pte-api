package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.scoring.ScoringService;
import com.pte.session.SessionService;
import com.pte.session.dto.event.SessionCancelledEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionCancelledNotificationListenerTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private ScoringService scoringService;

    @Mock
    private IdentityService identityService;

    @Mock
    private NotificationDispatchService dispatchService;

    @Test
    void onSessionCancelled_dispatchesOnlyAssignedActiveExamStaffOnce() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        User first = new User();
        first.setPublicId(firstId);
        first.setEmail("first@example.com");
        first.setStatus(UserStatus.ACTIVE);
        User second = new User();
        second.setPublicId(secondId);
        second.setEmail("second@example.com");
        second.setStatus(UserStatus.ACTIVE);
        when(sessionService.findAssignedProctorPublicIds(sessionId, tenantId)).thenReturn(List.of(firstId));
        when(scoringService.findAssignedExaminerPublicIds(tenantId, sessionId))
                .thenReturn(List.of(secondId, firstId));
        when(identityService.findActiveStaffInTenant(firstId, tenantId)).thenReturn(Optional.of(first));
        when(identityService.findActiveStaffInTenant(secondId, tenantId)).thenReturn(Optional.of(second));

        new SessionCancelledNotificationListener(sessionService, scoringService, identityService, dispatchService, com.pte.TestClocks.fixed())
                .onSessionCancelled(new SessionCancelledEvent(sessionId, tenantId, "Exam A", Instant.now()));

        ArgumentCaptor<NotificationType> types = ArgumentCaptor.forClass(NotificationType.class);
        verify(dispatchService, times(2)).dispatch(types.capture(), any(), any(), any(), any(), any());
        assertThat(types.getAllValues()).containsOnly(NotificationType.SESSION_CANCELLED);
    }
}
