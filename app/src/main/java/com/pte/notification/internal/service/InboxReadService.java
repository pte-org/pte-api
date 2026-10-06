package com.pte.notification.internal.service;

import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxReadFilter;
import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.dto.response.InboxItemResponse;
import com.pte.notification.internal.dto.response.InboxPageResponse;
import com.pte.notification.internal.dto.response.InboxReadAllResponse;
import com.pte.notification.internal.dto.response.InboxSnapshotResponse;
import com.pte.notification.internal.dto.response.InboxUnreadCountResponse;
import com.pte.notification.internal.exception.InboxNotificationException;
import com.pte.notification.internal.repository.InboxReadStore;
import com.pte.notification.internal.repository.InboxReadStore.Row;
import com.pte.notification.internal.repository.InboxReadStore.Snapshot;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Self-scoped inbox API; caller identity is always the query owner. */
@Service
@Transactional(timeout = 10)
public class InboxReadService {
    private final InboxReadStore store;
    private final Clock clock;

    public InboxReadService(InboxReadStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public InboxPageResponse list(CurrentUser caller, int requestedPage, int requestedSize, String rawFilter,
            String rawCategory, String rawSnapshot) {
        Scope scope = requireScope(caller);
        int page = normalizePage(requestedPage);
        int size = normalizeSize(requestedSize);
        InboxReadFilter filter = parseFilter(rawFilter);
        InboxCategory category = parseCategory(rawCategory);
        Instant now = clock.instant();
        Snapshot snapshot = resolveSnapshot(scope, rawSnapshot, now);
        InboxReadStore.Page result = store.page(scope.userId(), scope.tenantId(), filter, category,
                snapshot.upperSequence(), pageOffset(page, size), size);
        List<InboxItemResponse> items = result.rows().stream().map(InboxReadService::toResponse).toList();
        return new InboxPageResponse(new PagedResult<>(items, pageMeta(page, size, result.totalElements())),
                new InboxSnapshotResponse(snapshot.token().toString(), snapshot.upperSequence(), snapshot.expiresAt()),
                result.readRevision());
    }

    @Transactional(readOnly = true)
    public InboxItemResponse get(UUID itemPublicId, CurrentUser caller) {
        Scope scope = requireScope(caller);
        return store.findItem(itemPublicId, scope.userId(), scope.tenantId()).map(InboxReadService::toResponse)
                .orElseThrow(this::itemNotFound);
    }

    public InboxItemResponse markRead(UUID itemPublicId, CurrentUser caller) {
        Scope scope = requireScope(caller);
        return store.markRead(itemPublicId, scope.userId(), scope.tenantId(), clock.instant())
                .map(InboxReadService::toResponse).orElseThrow(this::itemNotFound);
    }

    public InboxUnreadCountResponse unreadCount(CurrentUser caller) {
        Scope scope = requireScope(caller);
        InboxReadStore.UnreadSummary summary = store.unreadCount(scope.userId(), scope.tenantId());
        return new InboxUnreadCountResponse(summary.count(), summary.readRevision());
    }

    public InboxReadAllResponse markAllRead(CurrentUser caller, String rawSnapshot) {
        Scope scope = requireScope(caller);
        Instant now = clock.instant();
        Snapshot snapshot = resolveOptionalSnapshot(scope, rawSnapshot, now);
        InboxReadStore.MarkAllResult result = store.markAll(scope.userId(), scope.tenantId(), snapshot, now);
        return new InboxReadAllResponse(result.markedCount(), result.watermark(), result.readRevision());
    }

    private Snapshot resolveSnapshot(Scope scope, String rawSnapshot, Instant now) {
        Snapshot provided = resolveOptionalSnapshot(scope, rawSnapshot, now);
        return provided != null ? provided
                : store.createSnapshot(scope.userId(), scope.tenantId(), now,
                        now.plusSeconds(InboxConstants.SNAPSHOT_TTL_SECONDS));
    }

    private Snapshot resolveOptionalSnapshot(Scope scope, String rawSnapshot, Instant now) {
        if (rawSnapshot == null || rawSnapshot.isBlank()) {
            return null;
        }
        UUID token;
        try {
            token = UUID.fromString(rawSnapshot.trim());
        } catch (IllegalArgumentException invalid) {
            throw invalidSnapshot();
        }
        return store.findSnapshot(token, scope.userId(), scope.tenantId(), now).orElseThrow(this::invalidSnapshot);
    }

    private Scope requireScope(CurrentUser caller) {
        if (caller == null || caller.userId() == null) {
            throw new AccessDeniedException(InboxConstants.ROLE_REQUIRED_MESSAGE);
        }
        boolean platform = caller.hasRole("PLATFORM_ADMIN");
        boolean host = caller.hasRole("HOST_ADMIN");
        if (!platform && !host) {
            throw new AccessDeniedException(InboxConstants.ROLE_REQUIRED_MESSAGE);
        }
        if (host && caller.tenantId() == null && !platform) {
            throw new AccessDeniedException(InboxConstants.ROLE_REQUIRED_MESSAGE);
        }
        if (platform && !host && caller.tenantId() != null) {
            throw new AccessDeniedException(InboxConstants.ROLE_REQUIRED_MESSAGE);
        }
        return new Scope(caller.userId(), host ? caller.tenantId() : null);
    }

    private int normalizePage(int requestedPage) {
        if (requestedPage < 0) {
            throw invalid(InboxConstants.PAGE_INVALID, InboxConstants.PAGE_INVALID_MESSAGE);
        }
        return requestedPage;
    }

    private int normalizeSize(int requestedSize) {
        if (requestedSize < 1 || requestedSize > InboxConstants.MAX_PAGE_SIZE) {
            throw invalid(InboxConstants.SIZE_INVALID, InboxConstants.SIZE_INVALID_MESSAGE);
        }
        return requestedSize;
    }

    private int pageOffset(int page, int size) {
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            throw invalid(InboxConstants.PAGE_INVALID, InboxConstants.PAGE_INVALID_MESSAGE);
        }
        return (int) offset;
    }

    private InboxReadFilter parseFilter(String rawFilter) {
        if (rawFilter == null || rawFilter.isBlank()) {
            return InboxReadFilter.ALL;
        }
        try {
            return InboxReadFilter.valueOf(rawFilter.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw invalid(InboxConstants.FILTER_INVALID, InboxConstants.FILTER_INVALID_MESSAGE);
        }
    }

    private InboxCategory parseCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return null;
        }
        try {
            return InboxCategory.valueOf(rawCategory.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw invalid(InboxConstants.CATEGORY_INVALID, InboxConstants.CATEGORY_INVALID_MESSAGE);
        }
    }

    private PageMeta pageMeta(int page, int size, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        boolean hasNext = page + 1 < totalPages;
        return new PageMeta(page, size, totalElements, totalPages, page == 0, !hasNext, hasNext, page > 0);
    }

    private static InboxItemResponse toResponse(Row row) {
        return new InboxItemResponse(row.publicId(), row.notificationType(), row.category(), row.importance(),
                row.title(), row.body(), row.targetType(), row.targetPublicId(), row.sequenceNo(), row.deliveredAt(),
                row.readAt());
    }

    private InboxNotificationException invalidSnapshot() {
        return invalid(InboxConstants.SNAPSHOT_INVALID, InboxConstants.SNAPSHOT_INVALID_MESSAGE);
    }

    private InboxNotificationException itemNotFound() {
        return invalidNotFound(InboxConstants.ITEM_NOT_FOUND, InboxConstants.ITEM_NOT_FOUND_MESSAGE);
    }

    private InboxNotificationException invalid(String code, String message) {
        return new InboxNotificationException(HttpStatus.BAD_REQUEST, code, message);
    }

    private InboxNotificationException invalidNotFound(String code, String message) {
        return new InboxNotificationException(HttpStatus.NOT_FOUND, code, message);
    }

    private record Scope(UUID userId, UUID tenantId) { }
}
