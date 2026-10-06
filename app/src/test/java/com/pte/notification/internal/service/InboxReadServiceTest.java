package com.pte.notification.internal.service;

import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxReadFilter;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.dto.response.InboxItemResponse;
import com.pte.notification.internal.dto.response.InboxPageResponse;
import com.pte.notification.internal.dto.response.InboxReadAllResponse;
import com.pte.notification.internal.exception.InboxNotificationException;
import com.pte.notification.internal.repository.InboxReadStore;
import com.pte.notification.internal.repository.InboxReadStore.Page;
import com.pte.notification.internal.repository.InboxReadStore.Row;
import com.pte.notification.internal.repository.InboxReadStore.Snapshot;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxReadServiceTest {
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ITEM = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID SNAPSHOT = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    @Mock
    private InboxReadStore store;

    private InboxReadService service;
    private CurrentUser host;

    @BeforeEach
    void setUp() {
        service = new InboxReadService(store, Clock.fixed(NOW, ZoneOffset.UTC));
        host = new CurrentUser(USER, TENANT, List.of("HOST_ADMIN"));
    }

    @Test
    void listCreatesServerSnapshotAndBindsPrincipalScope() {
        Snapshot snapshot = new Snapshot(SNAPSHOT, USER, TENANT, 42, 7, NOW.plusSeconds(900));
        Row row = row(null);
        when(store.createSnapshot(eq(USER), eq(TENANT), eq(NOW), eq(NOW.plusSeconds(900))))
                .thenReturn(snapshot);
        when(store.page(eq(USER), eq(TENANT), eq(InboxReadFilter.ALL), eq(null), eq(42L), eq(0), eq(20)))
                .thenReturn(new Page(List.of(row), 1, 7));

        InboxPageResponse response = service.list(host, 0, 20, "ALL", null, null);

        assertThat(response.page().data()).hasSize(1);
        assertThat(response.page().meta().totalElements()).isEqualTo(1);
        assertThat(response.snapshot().token()).isEqualTo(SNAPSHOT.toString());
        assertThat(response.snapshot().upperSequence()).isEqualTo(42);
        assertThat(response.readRevision()).isEqualTo(7);
        verify(store).page(USER, TENANT, InboxReadFilter.ALL, null, 42, 0, 20);
    }

    @Test
    void listRejectsInvalidPagingFilterAndCategoryWithStableCodes() {
        assertInvalid(() -> service.list(host, -1, 20, "ALL", null, null), InboxConstants.PAGE_INVALID);
        assertInvalid(() -> service.list(host, 0, 101, "ALL", null, null), InboxConstants.SIZE_INVALID);
        assertInvalid(() -> service.list(host, 0, 20, "NEW", null, null), InboxConstants.FILTER_INVALID);
        assertInvalid(() -> service.list(host, 0, 20, "ALL", "UNKNOWN", null), InboxConstants.CATEGORY_INVALID);
    }

    @Test
    void listRejectsSnapshotNotOwnedByCurrentRecipient() {
        when(store.findSnapshot(eq(SNAPSHOT), eq(USER), eq(TENANT), eq(NOW))).thenReturn(Optional.empty());

        assertInvalid(() -> service.list(host, 0, 20, "ALL", null, SNAPSHOT.toString()),
                InboxConstants.SNAPSHOT_INVALID);
    }

    @Test
    void listRejectsMalformedSnapshotWithoutQueryingStore() {
        assertInvalid(() -> service.list(host, 0, 20, "ALL", null, "not-a-uuid"),
                InboxConstants.SNAPSHOT_INVALID);
    }

    @Test
    void platformAdminUsesNullTenantScopeWithoutAcceptingTenantOverride() {
        CurrentUser platform = new CurrentUser(USER, null, List.of("PLATFORM_ADMIN"));
        Snapshot snapshot = new Snapshot(SNAPSHOT, USER, null, 0, 0, NOW.plusSeconds(900));
        when(store.createSnapshot(eq(USER), eq(null), eq(NOW), eq(NOW.plusSeconds(900))))
                .thenReturn(snapshot);
        when(store.page(eq(USER), eq(null), eq(InboxReadFilter.ALL), eq(null), eq(0L), eq(0), eq(20)))
                .thenReturn(new Page(List.of(), 0, 0));

        InboxPageResponse response = service.list(platform, 0, 20, "ALL", null, null);

        assertThat(response.page().data()).isEmpty();
        verify(store).page(USER, null, InboxReadFilter.ALL, null, 0, 0, 20);
    }

    @Test
    void nonHostAndNonPlatformPrincipalCannotReadInbox() {
        CurrentUser student = new CurrentUser(USER, TENANT, List.of("STUDENT"));

        assertThatThrownBy(() -> service.unreadCount(student))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void markReadAndMarkAllUseOwnScopeAndServerSnapshot() {
        Row read = row(NOW);
        when(store.markRead(eq(ITEM), eq(USER), eq(TENANT), eq(NOW))).thenReturn(Optional.of(read));
        Snapshot snapshot = new Snapshot(SNAPSHOT, USER, TENANT, 42, 7, NOW.plusSeconds(900));
        when(store.findSnapshot(eq(SNAPSHOT), eq(USER), eq(TENANT), eq(NOW))).thenReturn(Optional.of(snapshot));
        when(store.markAll(eq(USER), eq(TENANT), eq(snapshot), eq(NOW)))
                .thenReturn(new InboxReadStore.MarkAllResult(3, 42, 8));

        InboxItemResponse item = service.markRead(ITEM, host);
        InboxReadAllResponse all = service.markAllRead(host, SNAPSHOT.toString());

        assertThat(item.readAt()).isEqualTo(NOW);
        assertThat(all.markedCount()).isEqualTo(3);
        assertThat(all.watermark()).isEqualTo(42);
        assertThat(all.readRevision()).isEqualTo(8);
        verify(store).markRead(ITEM, USER, TENANT, NOW);
        verify(store).markAll(USER, TENANT, snapshot, NOW);
    }

    @Test
    void missingItemIsIndistinguishableFromForeignItem() {
        when(store.findItem(eq(ITEM), eq(USER), eq(TENANT))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ITEM, host))
                .isInstanceOf(InboxNotificationException.class)
                .extracting("code").isEqualTo(InboxConstants.ITEM_NOT_FOUND);
    }

    private static Row row(Instant readAt) {
        return new Row(ITEM, UUID.randomUUID(), USER, TENANT, 42,
                InboxNotificationType.PLATFORM_ANNOUNCEMENT, InboxCategory.SYSTEM_NOTICE,
                InboxImportance.INFO, "Maintenance", "Short notice", InboxTargetType.ANNOUNCEMENT,
                UUID.randomUUID(), NOW, readAt);
    }

    private static void assertInvalid(ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOf(InboxNotificationException.class)
                .extracting("code").isEqualTo(code);
    }
}
