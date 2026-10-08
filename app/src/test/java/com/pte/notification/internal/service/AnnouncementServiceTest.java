package com.pte.notification.internal.service;

import com.pte.identity.IdentityRoleMember;
import com.pte.identity.domain.Role;
import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.dto.request.AnnouncementCreateRequest;
import com.pte.notification.internal.dto.request.AnnouncementPublishRequest;
import com.pte.notification.internal.dto.request.AnnouncementUpdateRequest;
import com.pte.notification.internal.exception.InboxNotificationException;
import com.pte.notification.internal.repository.InboxAnnouncementStore;
import com.pte.notification.internal.repository.InboxAnnouncementStore.AnnouncementRow;
import com.pte.notification.internal.repository.InboxAnnouncementStore.DeliverySummary;
import com.pte.notification.internal.repository.InboxDeliveryStore;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-03T13:00:00Z");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID ANNOUNCEMENT = UUID.fromString("00000000-0000-0000-0000-000000000502");
    private static final UUID CONTENT = UUID.fromString("00000000-0000-0000-0000-000000000503");
    private static final UUID HOST_A = UUID.fromString("00000000-0000-0000-0000-000000000504");
    private static final UUID HOST_B = UUID.fromString("00000000-0000-0000-0000-000000000505");
    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000506");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000507");

    @Mock private InboxAnnouncementStore store;
    @Mock private InboxAppendService appender;
    @Mock private InboxDeliveryStore delivery;
    @Mock private com.pte.identity.IdentityService identity;
    @Mock private com.pte.tenancy.TenancyService tenancy;
    @Mock private AuditLogService audit;

    private AnnouncementService service;
    private CurrentUser admin;
    private CurrentUser manager;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(store, appender, delivery, identity, tenancy, audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
        admin = new CurrentUser(ADMIN, null, List.of("PLATFORM_ADMIN"));
        manager = new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000508"), null,
                List.of("PLATFORM_MANAGER"));
    }

    @Test
    void createTrimsDraftAndKeepsCorrectionMetadata() {
        AnnouncementCreateRequest request = new AnnouncementCreateRequest("  Maintenance  ", "  Restart at 02:00  ",
                InboxCategory.MAINTENANCE, InboxImportance.IMPORTANT, NOW, NOW.plusSeconds(3600), null);
        AnnouncementRow row = draft(0L);
        when(store.insert(any(), eq(ADMIN), eq("Maintenance"), eq("Restart at 02:00"), eq(InboxCategory.MAINTENANCE),
                eq(InboxImportance.IMPORTANT), eq(NOW), eq(NOW.plusSeconds(3600)), eq(null), any(Instant.class)))
                .thenReturn(row);

        assertThat(service.create(admin, request).publicId()).isEqualTo(ANNOUNCEMENT);
        verify(audit).record(eq(admin), eq("ANNOUNCEMENT"), anyString(), eq("CREATE_DRAFT"), any());
    }

    @Test
    void managerCanCreateAndPublishAnnouncementDraft() {
        AnnouncementCreateRequest request = new AnnouncementCreateRequest("Maintenance", "Restart at 03:00",
                InboxCategory.MAINTENANCE, InboxImportance.IMPORTANT, NOW, NOW.plusSeconds(3600), null);
        when(store.insert(any(), eq(manager.userId()), eq("Maintenance"), eq("Restart at 03:00"),
                eq(InboxCategory.MAINTENANCE), eq(InboxImportance.IMPORTANT), eq(NOW), eq(NOW.plusSeconds(3600)),
                eq(null), any(Instant.class))).thenReturn(draft(0L));
        when(store.lockForUpdate(ANNOUNCEMENT)).thenReturn(Optional.of(draft(0L)));
        when(identity.findActiveRoleMembers(Role.HOST_ADMIN)).thenReturn(List.of());
        when(tenancy.findActiveTenantIds(Set.of())).thenReturn(Set.of());
        when(appender.append(any(InboxNotificationRequested.class))).thenReturn(CONTENT);
        when(store.markPublished(eq(ANNOUNCEMENT), eq(0L), eq(CONTENT), eq(NOW))).thenReturn(Optional.of(published(1L)));

        assertThat(service.create(manager, request).publicId()).isEqualTo(ANNOUNCEMENT);
        assertThat(service.publish(manager, ANNOUNCEMENT, new AnnouncementPublishRequest(0L)).published()).isTrue();
        verify(audit).record(eq(manager), eq("ANNOUNCEMENT"), eq(ANNOUNCEMENT.toString()),
                eq(InboxConstants.AUDIT_PUBLISH), any());
    }

    @Test
    void updateRejectsStaleDraftVersionBeforeWriting() {
        when(store.find(ANNOUNCEMENT)).thenReturn(Optional.of(draft(3L)));

        assertThatThrownBy(() -> service.update(admin, ANNOUNCEMENT,
                new AnnouncementUpdateRequest("Title", "Body", InboxCategory.SYSTEM_NOTICE,
                        InboxImportance.INFO, null, null, 2L)))
                .isInstanceOf(InboxNotificationException.class)
                .extracting("code").isEqualTo(InboxConstants.ANNOUNCEMENT_VERSION_CONFLICT);
        verify(store, never()).updateDraft(any(), any(Long.class), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void publishSnapshotsEligibleHostsAndReplayDoesNotAppendAgain() {
        AnnouncementRow draft = draft(4L);
        AnnouncementRow published = published(5L);
        when(store.lockForUpdate(ANNOUNCEMENT)).thenReturn(Optional.of(draft), Optional.of(published));
        when(identity.findActiveRoleMembers(Role.HOST_ADMIN)).thenReturn(List.of(
                new IdentityRoleMember(HOST_B, TENANT_B), new IdentityRoleMember(HOST_A, TENANT_A)));
        when(tenancy.findActiveTenantIds(Set.of(TENANT_A, TENANT_B))).thenReturn(Set.of(TENANT_A, TENANT_B));
        when(appender.append(any(InboxNotificationRequested.class))).thenReturn(CONTENT);
        when(store.markPublished(eq(ANNOUNCEMENT), eq(4L), eq(CONTENT), eq(NOW))).thenReturn(Optional.of(published));

        assertThat(service.publish(admin, ANNOUNCEMENT, new AnnouncementPublishRequest(4L)).published()).isTrue();
        ArgumentCaptor<InboxNotificationRequested> captured = ArgumentCaptor.forClass(InboxNotificationRequested.class);
        verify(appender).append(captured.capture());
        assertThat(captured.getValue().recipients()).extracting("userPublicId").containsExactly(HOST_A, HOST_B);
        assertThat(service.publish(admin, ANNOUNCEMENT, new AnnouncementPublishRequest(4L)).publishedContentPublicId())
                .isEqualTo(CONTENT);
        verify(appender).append(any(InboxNotificationRequested.class));
    }

    @Test
    void publishRejectsStaleVersionAndPublishedMutation() {
        when(store.lockForUpdate(ANNOUNCEMENT)).thenReturn(Optional.of(draft(7L)));
        assertThatThrownBy(() -> service.publish(admin, ANNOUNCEMENT, new AnnouncementPublishRequest(6L)))
                .isInstanceOf(InboxNotificationException.class)
                .extracting("code").isEqualTo(InboxConstants.ANNOUNCEMENT_VERSION_CONFLICT);

        when(store.lockForUpdate(ANNOUNCEMENT)).thenReturn(Optional.of(published(8L)));
        assertThat(service.publish(admin, ANNOUNCEMENT, new AnnouncementPublishRequest(0L)).published()).isTrue();
        verify(appender, never()).append(any());
    }

    @Test
    void audiencePreviewExcludesHostsFromInactiveTenants() {
        when(store.find(ANNOUNCEMENT)).thenReturn(Optional.of(draft(0L)));
        when(identity.findActiveRoleMembers(Role.HOST_ADMIN)).thenReturn(List.of(
                new IdentityRoleMember(HOST_A, TENANT_A), new IdentityRoleMember(HOST_B, TENANT_B)));
        when(tenancy.findActiveTenantIds(Set.of(TENANT_A, TENANT_B))).thenReturn(Set.of(TENANT_A));

        var preview = service.preview(admin, ANNOUNCEMENT);

        assertThat(preview.eligibleTenantCount()).isEqualTo(1);
        assertThat(preview.eligibleUserCount()).isEqualTo(1);
    }

    @Test
    void retryResetsOnlyFailedDeliveriesForThePublishedContent() {
        when(store.find(ANNOUNCEMENT)).thenReturn(Optional.of(published(8L)), Optional.of(published(8L)));
        when(delivery.retryFailedForContent(CONTENT, NOW)).thenReturn(2);

        assertThat(service.retryDelivery(admin, ANNOUNCEMENT).publishedContentPublicId()).isEqualTo(CONTENT);

        verify(delivery).retryFailedForContent(CONTENT, NOW);
    }

    private static AnnouncementRow draft(long version) {
        return new AnnouncementRow(ANNOUNCEMENT, ADMIN, "Title", "Body", InboxCategory.SYSTEM_NOTICE,
                InboxImportance.INFO, null, null, null, null, null, version, NOW, NOW, DeliverySummary.empty());
    }

    private static AnnouncementRow published(long version) {
        return new AnnouncementRow(ANNOUNCEMENT, ADMIN, "Title", "Body", InboxCategory.SYSTEM_NOTICE,
                InboxImportance.INFO, null, null, CONTENT, NOW, null, version, NOW, NOW,
                new DeliverySummary(2, 0, 2, 0, 0, 0));
    }

}
