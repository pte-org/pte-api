package com.pte.notification.internal.listener;

import com.pte.billing.TenantApplicationSubmittedEvent;
import com.pte.identity.IdentityRoleMember;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.service.InboxAppendService;
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
class TenantApplicationInboxNotificationListenerTest {
    @Mock private IdentityService identity;
    @Mock private InboxAppendService appender;

    @Test
    void committedApplicationMapsToEveryActivePlatformAdmin() {
        UUID application = UUID.randomUUID();
        UUID adminOne = UUID.randomUUID();
        UUID adminTwo = UUID.randomUUID();
        when(identity.findActiveRoleMembers(Role.PLATFORM_ADMIN)).thenReturn(List.of(
                new IdentityRoleMember(adminTwo, null), new IdentityRoleMember(adminOne, null)));

        new TenantApplicationInboxNotificationListener(identity, appender).onApplicationSubmitted(
                new TenantApplicationSubmittedEvent(application, "Acme", "ACME", "owner@example.com"));

        ArgumentCaptor<InboxNotificationRequested> captured = ArgumentCaptor.forClass(InboxNotificationRequested.class);
        verify(appender).append(captured.capture());
        InboxNotificationRequested request = captured.getValue();
        assertThat(request.type()).isEqualTo(InboxNotificationType.APPLICATION_SUBMITTED);
        assertThat(request.category()).isEqualTo(InboxCategory.APPLICATION);
        assertThat(request.importance()).isEqualTo(InboxImportance.INFO);
        assertThat(request.targetType()).isEqualTo(InboxTargetType.APPLICATION);
        assertThat(request.targetPublicId()).isEqualTo(application);
        assertThat(request.recipients()).extracting("userPublicId").containsExactlyInAnyOrder(adminOne, adminTwo);
    }

    @Test
    void applicationWithoutEligibleAdminStillRecordsTheCommittedEvent() {
        when(identity.findActiveRoleMembers(Role.PLATFORM_ADMIN)).thenReturn(List.of());

        new TenantApplicationInboxNotificationListener(identity, appender).onApplicationSubmitted(
                new TenantApplicationSubmittedEvent(UUID.randomUUID(), "Acme", "ACME", "owner@example.com"));

        verify(appender).append(any(InboxNotificationRequested.class));
    }
}
