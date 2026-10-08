package com.pte.notification.internal.service;

import com.pte.identity.IdentityRoleMember;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.InboxRecipient;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.dto.request.AnnouncementCreateRequest;
import com.pte.notification.internal.dto.request.AnnouncementDeleteRequest;
import com.pte.notification.internal.dto.request.AnnouncementPublishRequest;
import com.pte.notification.internal.dto.request.AnnouncementUpdateRequest;
import com.pte.notification.internal.dto.response.AnnouncementAudiencePreviewResponse;
import com.pte.notification.internal.dto.response.AnnouncementDeliverySummary;
import com.pte.notification.internal.dto.response.AnnouncementResponse;
import com.pte.notification.internal.exception.InboxNotificationException;
import com.pte.notification.internal.repository.InboxAnnouncementStore;
import com.pte.notification.internal.repository.InboxAnnouncementStore.AnnouncementRow;
import com.pte.notification.internal.repository.InboxDeliveryStore;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import com.pte.tenancy.TenancyService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Platform announcement lifecycle and immutable fan-out boundary. */
@Service
@Transactional(timeout = 15)
public class AnnouncementService {
    private final InboxAnnouncementStore store;
    private final InboxAppendService appender;
    private final InboxDeliveryStore delivery;
    private final IdentityService identity;
    private final TenancyService tenancy;
    private final AuditLogService audit;
    private final Clock clock;

    public AnnouncementService(InboxAnnouncementStore store, InboxAppendService appender,
            InboxDeliveryStore delivery, IdentityService identity, TenancyService tenancy,
            AuditLogService audit, Clock clock) {
        this.store = store;
        this.appender = appender;
        this.delivery = delivery;
        this.identity = identity;
        this.tenancy = tenancy;
        this.audit = audit;
        this.clock = clock;
    }

    public PagedResult<AnnouncementResponse> list(CurrentUser caller, int requestedPage, int requestedSize) {
        requireOperation(caller, PlatformOperation.ANNOUNCEMENT_READ, "ANNOUNCEMENT_READ");
        int page = requestedPage < 0 ? invalidPage() : requestedPage;
        int size = requestedSize < 1 || requestedSize > InboxConstants.MAX_PAGE_SIZE ? invalidSize() : requestedSize;
        long offsetLong = (long) page * size;
        if (offsetLong > Integer.MAX_VALUE) {
            throw invalid(InboxConstants.ANNOUNCEMENT_PAGE_INVALID, InboxConstants.ANNOUNCEMENT_PAGE_INVALID_MESSAGE);
        }
        InboxAnnouncementStore.Page result = store.page((int) offsetLong, size);
        List<AnnouncementResponse> rows = result.rows().stream().map(AnnouncementService::toResponse).toList();
        int totalPages = result.totalElements() == 0 ? 0 : (int) ((result.totalElements() + size - 1) / size);
        PageMeta meta = new PageMeta(page, size, result.totalElements(), totalPages, page == 0,
                page + 1 >= totalPages, page + 1 < totalPages, page > 0);
        return new PagedResult<>(rows, meta);
    }

    public AnnouncementResponse create(CurrentUser caller, AnnouncementCreateRequest request) {
        requireOperation(caller, PlatformOperation.ANNOUNCEMENT_DRAFT_WRITE, "ANNOUNCEMENT_CREATE_DRAFT");
        if (request == null) {
            throw invalid(InboxConstants.ANNOUNCEMENT_INVALID, InboxConstants.ANNOUNCEMENT_INVALID_MESSAGE);
        }
        validateDraft(request.title(), request.body(), request.category(), request.importance(),
                request.affectedFrom(), request.affectedUntil());
        if (request.correctionOfPublicId() != null) {
            AnnouncementRow original = store.find(request.correctionOfPublicId()).orElseThrow(this::correctionInvalid);
            if (!original.published()) {
                throw correctionInvalid();
            }
        }
        UUID publicId = UUID.randomUUID();
        Instant now = clock.instant();
        AnnouncementRow row = store.insert(publicId, caller.userId(), request.title().trim(), request.body().trim(),
                request.category(), request.importance(), request.affectedFrom(), request.affectedUntil(),
                request.correctionOfPublicId(), now);
        audit.record(caller, InboxConstants.ANNOUNCEMENT_AGGREGATE, publicId.toString(),
                InboxConstants.AUDIT_CREATE_DRAFT, InboxConstants.AUDIT_CREATE_DRAFT_SUMMARY);
        return toResponse(row);
    }

    public AnnouncementResponse get(CurrentUser caller, UUID publicId) {
        requireOperation(caller, PlatformOperation.ANNOUNCEMENT_READ, "ANNOUNCEMENT_READ");
        return store.find(publicId).map(AnnouncementService::toResponse).orElseThrow(this::notFound);
    }

    public AnnouncementResponse update(CurrentUser caller, UUID publicId, AnnouncementUpdateRequest request) {
        requireOperation(caller, PlatformOperation.ANNOUNCEMENT_DRAFT_WRITE, "ANNOUNCEMENT_UPDATE_DRAFT");
        if (request == null) {
            throw invalid(InboxConstants.ANNOUNCEMENT_INVALID, InboxConstants.ANNOUNCEMENT_INVALID_MESSAGE);
        }
        AnnouncementRow current = store.find(publicId).orElseThrow(this::notFound);
        requireDraft(current);
        validateDraft(request.title(), request.body(), request.category(), request.importance(),
                request.affectedFrom(), request.affectedUntil());
        if (request.expectedDraftVersion() == null || request.expectedDraftVersion() != current.version()) {
            throw versionConflict();
        }
        boolean updated = store.updateDraft(publicId, current.version(), request.title().trim(), request.body().trim(),
                request.category(), request.importance(), request.affectedFrom(), request.affectedUntil(), clock.instant());
        if (!updated) {
            throw versionConflict();
        }
        audit.record(caller, InboxConstants.ANNOUNCEMENT_AGGREGATE, publicId.toString(),
                InboxConstants.AUDIT_UPDATE_DRAFT, InboxConstants.AUDIT_UPDATE_DRAFT_SUMMARY);
        return store.find(publicId).map(AnnouncementService::toResponse).orElseThrow(this::notFound);
    }

    public void delete(CurrentUser caller, UUID publicId, AnnouncementDeleteRequest request) {
        requireOperation(caller, PlatformOperation.ANNOUNCEMENT_DRAFT_WRITE, "ANNOUNCEMENT_DELETE_DRAFT");
        AnnouncementRow current = store.find(publicId).orElseThrow(this::notFound);
        requireDraft(current);
        if (request == null || request.expectedDraftVersion() == null
                || request.expectedDraftVersion() != current.version()
                || !store.deleteDraft(publicId, current.version(), clock.instant())) {
            throw versionConflict();
        }
        audit.record(caller, InboxConstants.ANNOUNCEMENT_AGGREGATE, publicId.toString(),
                InboxConstants.AUDIT_DELETE_DRAFT, InboxConstants.AUDIT_DELETE_DRAFT_SUMMARY);
    }

    public AnnouncementAudiencePreviewResponse preview(CurrentUser caller, UUID publicId) {
        requireOperation(caller, PlatformOperation.ANNOUNCEMENT_READ, "ANNOUNCEMENT_PREVIEW");
        store.find(publicId).orElseThrow(this::notFound);
        Audience audience = resolveAudience();
        return new AnnouncementAudiencePreviewResponse(audience.tenantIds().size(), audience.recipients().size());
    }

    public AnnouncementResponse publish(CurrentUser caller, UUID publicId, AnnouncementPublishRequest request) {
        requireOperation(caller, PlatformOperation.ANNOUNCEMENT_PUBLISH, "ANNOUNCEMENT_PUBLISH");
        AnnouncementRow current = store.lockForUpdate(publicId).orElseThrow(this::notFound);
        if (current.published()) {
            return toResponse(current);
        }
        if (request == null || request.expectedDraftVersion() == null
                || request.expectedDraftVersion() != current.version()) {
            throw versionConflict();
        }
        Audience audience = resolveAudience();
        InboxNotificationRequested event = new InboxNotificationRequested(
                InboxConstants.SCHEMA_VERSION,
                "announcement:%s:published:v%s".formatted(publicId, current.version()),
                com.pte.notification.domain.enums.InboxNotificationType.PLATFORM_ANNOUNCEMENT,
                current.category(), current.importance(), current.title(), current.body(),
                com.pte.notification.domain.enums.InboxTargetType.ANNOUNCEMENT, publicId, audience.recipients());
        UUID contentPublicId = appender.append(event);
        AnnouncementRow published = store.markPublished(publicId, current.version(), contentPublicId, clock.instant())
                .orElseThrow(this::versionConflict);
        audit.record(caller, InboxConstants.ANNOUNCEMENT_AGGREGATE, publicId.toString(),
                InboxConstants.AUDIT_PUBLISH, InboxConstants.AUDIT_PUBLISH_SUMMARY.formatted(audience.recipients().size()));
        return toResponse(published);
    }

    public AnnouncementResponse retryDelivery(CurrentUser caller, UUID publicId) {
        requireOperation(caller, PlatformOperation.ANNOUNCEMENT_RETRY, "ANNOUNCEMENT_RETRY");
        AnnouncementRow current = store.find(publicId).orElseThrow(this::notFound);
        if (!current.published()) {
            throw invalid(InboxConstants.ANNOUNCEMENT_NOT_PUBLISHED, InboxConstants.ANNOUNCEMENT_NOT_PUBLISHED_MESSAGE);
        }
        int retried = delivery.retryFailedForContent(current.publishedContentPublicId(), clock.instant());
        audit.record(caller, InboxConstants.ANNOUNCEMENT_AGGREGATE, publicId.toString(),
                InboxConstants.AUDIT_RETRY_DELIVERY, InboxConstants.AUDIT_RETRY_DELIVERY_SUMMARY.formatted(retried));
        return store.find(publicId).map(AnnouncementService::toResponse).orElseThrow(this::notFound);
    }

    private Audience resolveAudience() {
        List<IdentityRoleMember> members = identity.findActiveRoleMembers(Role.HOST_ADMIN).stream()
                .filter(member -> member.userPublicId() != null && member.tenantId() != null).toList();
        Set<UUID> candidateTenants = members.stream().map(IdentityRoleMember::tenantId).collect(Collectors.toSet());
        Set<UUID> activeTenants = tenancy.findActiveTenantIds(candidateTenants);
        List<InboxRecipient> recipients = members.stream()
                .filter(member -> activeTenants.contains(member.tenantId()))
                .map(member -> new InboxRecipient(member.userPublicId(), member.tenantId()))
                .sorted(Comparator.comparing(InboxRecipient::userPublicId))
                .toList();
        return new Audience(activeTenants, recipients);
    }

    private static AnnouncementResponse toResponse(AnnouncementRow row) {
        var summary = row.delivery();
        return new AnnouncementResponse(row.publicId(), row.authorUserPublicId(), row.title(), row.body(), row.category(),
                row.importance(), row.affectedFrom(), row.affectedUntil(), row.publishedContentPublicId(),
                row.publishedAt(), row.correctionOfPublicId(), row.version(), row.published(), row.createdAt(),
                row.updatedAt(), new AnnouncementDeliverySummary(summary.audienceCount(), summary.pendingCount(),
                        summary.deliveredCount(), summary.failedCount(), summary.suppressedCount(), summary.readCount()));
    }

    private static void validateDraft(String title, String body, InboxCategory category,
            com.pte.notification.domain.enums.InboxImportance importance, Instant affectedFrom, Instant affectedUntil) {
        if (!validText(title, InboxConstants.TITLE_LIMIT) || !validText(body, InboxConstants.BODY_LIMIT)
                || category == null || (category != InboxCategory.SYSTEM_NOTICE && category != InboxCategory.MAINTENANCE)
                || importance == null || (affectedUntil != null && (affectedFrom == null || !affectedUntil.isAfter(affectedFrom)))) {
            throw invalidStatic(InboxConstants.ANNOUNCEMENT_INVALID, InboxConstants.ANNOUNCEMENT_INVALID_MESSAGE);
        }
    }

    private static boolean validText(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max && value.indexOf('\0') < 0;
    }

    private void requireOperation(CurrentUser caller, PlatformOperation operation, String action) {
        if (PlatformOperationPolicy.can(caller, operation)) {
            return;
        }
        if (caller != null) {
            audit.recordFailure(caller, InboxConstants.ANNOUNCEMENT_AGGREGATE, "unknown",
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, action);
        }
        throw new AccessDeniedException(InboxConstants.PLATFORM_OPERATION_REQUIRED_MESSAGE);
    }

    private void requireDraft(AnnouncementRow row) {
        if (row.published()) {
            throw invalid(HttpStatus.CONFLICT, InboxConstants.ANNOUNCEMENT_PUBLISHED,
                    InboxConstants.ANNOUNCEMENT_PUBLISHED_MESSAGE);
        }
    }

    private int invalidPage() { throw invalid(InboxConstants.ANNOUNCEMENT_PAGE_INVALID, InboxConstants.ANNOUNCEMENT_PAGE_INVALID_MESSAGE); }
    private int invalidSize() { throw invalid(InboxConstants.ANNOUNCEMENT_SIZE_INVALID, InboxConstants.ANNOUNCEMENT_SIZE_INVALID_MESSAGE); }
    private InboxNotificationException notFound() { return invalid(HttpStatus.NOT_FOUND, InboxConstants.ANNOUNCEMENT_NOT_FOUND, InboxConstants.ANNOUNCEMENT_NOT_FOUND_MESSAGE); }
    private InboxNotificationException versionConflict() { return invalid(HttpStatus.CONFLICT, InboxConstants.ANNOUNCEMENT_VERSION_CONFLICT, InboxConstants.ANNOUNCEMENT_VERSION_CONFLICT_MESSAGE); }
    private InboxNotificationException correctionInvalid() { return invalid(HttpStatus.BAD_REQUEST, InboxConstants.ANNOUNCEMENT_CORRECTION_INVALID, InboxConstants.ANNOUNCEMENT_CORRECTION_INVALID_MESSAGE); }
    private InboxNotificationException invalid(String code, String message) { return invalid(HttpStatus.BAD_REQUEST, code, message); }
    private static InboxNotificationException invalidStatic(String code, String message) { return new InboxNotificationException(HttpStatus.BAD_REQUEST, code, message); }
    private InboxNotificationException invalid(HttpStatus status, String code, String message) { return new InboxNotificationException(status, code, message); }

    private record Audience(Set<UUID> tenantIds, List<InboxRecipient> recipients) { }
}
