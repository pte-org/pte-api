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
import com.pte.notification.internal.service.InboxAppendService;
import com.pte.session.dto.event.ExamPreviewReadyEvent;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamPreviewReadyNotificationListenerTest {

    @Mock
    private IdentityService identityService;
    @Mock
    private TenancyService tenancyService;
    @Mock
    private InboxAppendService appender;

    private ExamPreviewReadyNotificationListener listener;
    private UUID sessionId;
    private UUID tenantId;
    private UUID snapshotId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        listener = new ExamPreviewReadyNotificationListener(identityService, tenancyService, appender);
        sessionId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        snapshotId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        when(tenancyService.isActiveTenant(tenantId)).thenReturn(true);
    }

    @Test
    void appendsOneStableSessionNotificationToTheGeneratingHost() {
        when(identityService.findActiveRoleMembers(tenantId, Role.HOST_ADMIN))
                .thenReturn(List.of(new IdentityRoleMember(actorId, tenantId)));
        listener.onPreviewReady(event());

        ArgumentCaptor<InboxNotificationRequested> captor =
                ArgumentCaptor.forClass(InboxNotificationRequested.class);
        verify(appender).append(captor.capture());
        InboxNotificationRequested request = captor.getValue();

        assertThat(request.eventKey()).isEqualTo(
                "session:%s:preview-ready:%s:%s".formatted(sessionId, snapshotId, actorId));
        assertThat(request.type()).isEqualTo(InboxNotificationType.SESSION_PREVIEW_READY);
        assertThat(request.category()).isEqualTo(InboxCategory.SESSION);
        assertThat(request.importance()).isEqualTo(InboxImportance.INFO);
        assertThat(request.targetType()).isEqualTo(InboxTargetType.SESSION);
        assertThat(request.targetPublicId()).isEqualTo(sessionId);
        assertThat(request.title()).isEqualTo("Exam preview ready");
        assertThat(request.body()).contains("Demo exam", "FPT-261010-K7QM");
        assertThat(request.recipients()).containsExactly(new InboxRecipient(actorId, tenantId));
    }

    @Test
    void doesNotBroadcastWhenTheGeneratingActorIsNoLongerAnActiveHost() {
        when(identityService.findActiveRoleMembers(tenantId, Role.HOST_ADMIN)).thenReturn(List.of());

        listener.onPreviewReady(event());

        verify(appender, never()).append(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignoresAnInactiveTenantBeforeResolvingRecipients() {
        when(tenancyService.isActiveTenant(tenantId)).thenReturn(false);

        listener.onPreviewReady(event());

        verify(identityService, never()).findActiveRoleMembers(tenantId, Role.HOST_ADMIN);
        verify(appender, never()).append(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotNotifyAHostMemberReturnedForAnotherTenant() {
        when(identityService.findActiveRoleMembers(tenantId, Role.HOST_ADMIN))
                .thenReturn(List.of(new IdentityRoleMember(actorId, UUID.randomUUID())));

        listener.onPreviewReady(event());

        verify(appender, never()).append(org.mockito.ArgumentMatchers.any());
    }

    private ExamPreviewReadyEvent event() {
        return new ExamPreviewReadyEvent(sessionId, tenantId, snapshotId, "FPT-261010-K7QM", "Demo exam",
                actorId, Instant.parse("2026-10-29T00:00:00Z"));
    }
}
