package com.pte.shared.audit;

import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.dto.AuditLogResponse;
import com.pte.shared.audit.internal.constant.AuditLogConstants;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** In-transaction append and tenant- or platform-scoped read API for audit records. */
@Service
public class AuditLogService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(CurrentUser caller, String aggregateType, String aggregateId, String action, String summary) {
        recordInternal(caller, aggregateType, aggregateId, null, null, action, summary);
    }

    @Transactional
    public void record(CurrentUser caller, String aggregateType, String aggregateId, String resourceName,
            String resourceCode, String action, String summary) {
        recordInternal(caller, aggregateType, aggregateId, resourceName, resourceCode, action, summary);
    }

    /**
     * Persists a validation/audit event even when the business transaction is
     * about to roll back because the requested transition was invalid.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(CurrentUser caller, String aggregateType, String aggregateId, String action,
            String summary) {
        recordInternal(caller, aggregateType, aggregateId, null, null, action, summary);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(CurrentUser caller, String aggregateType, String aggregateId, String resourceName,
            String resourceCode, String action, String summary) {
        recordInternal(caller, aggregateType, aggregateId, resourceName, resourceCode, action, summary);
    }

    private void recordInternal(CurrentUser caller, String aggregateType, String aggregateId, String resourceName,
            String resourceCode, String action, String summary) {
        auditLogRepository.save(newAuditLog(caller, aggregateType, aggregateId, resourceName, resourceCode,
                action, summary));
    }

    private AuditLog newAuditLog(CurrentUser caller, String aggregateType, String aggregateId, String resourceName,
            String resourceCode, String action, String summary) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorRole(String.join(AuditLogConstants.ROLE_SEPARATOR, caller.roles()));
        auditLog.setTenantId(caller.tenantId());
        auditLog.setActorUserId(caller.userId());
        auditLog.setAggregateType(aggregateType);
        auditLog.setAggregateId(aggregateId);
        auditLog.setResourceName(resourceName);
        auditLog.setResourceCode(resourceCode);
        auditLog.setAction(action);
        auditLog.setSummary(summary);
        return auditLog;
    }

    @Transactional(readOnly = true)
    public PagedResult<AuditLogResponse> list(CurrentUser caller, String aggregateType, int requestedPage,
            int requestedSize) {
        return list(caller, aggregateType, null, requestedPage, requestedSize);
    }

    @Transactional(readOnly = true)
    public PagedResult<AuditLogResponse> list(CurrentUser caller, String aggregateType, String search,
            int requestedPage, int requestedSize) {
        return list(caller, aggregateType, search, null, null, requestedPage, requestedSize);
    }

    @Transactional(readOnly = true)
    public PagedResult<AuditLogResponse> list(CurrentUser caller, String aggregateType, String search,
            LocalDate createdFrom, LocalDate createdTo, int requestedPage, int requestedSize) {
        return listScoped(caller.tenantId(), aggregateType, search, createdFrom, createdTo,
                requestedPage, requestedSize, false);
    }

    /** Platform-wide audit trail. Only tenantless rows are returned. */
    @Transactional(readOnly = true)
    public PagedResult<AuditLogResponse> listPlatform(CurrentUser caller, String aggregateType,
            int requestedPage, int requestedSize) {
        return listPlatform(caller, aggregateType, null, requestedPage, requestedSize);
    }

    @Transactional(readOnly = true)
    public PagedResult<AuditLogResponse> listPlatform(CurrentUser caller, String aggregateType, String search,
            int requestedPage, int requestedSize) {
        return listPlatform(caller, aggregateType, search, null, null, requestedPage, requestedSize);
    }

    @Transactional(readOnly = true)
    public PagedResult<AuditLogResponse> listPlatform(CurrentUser caller, String aggregateType, String search,
            LocalDate createdFrom, LocalDate createdTo, int requestedPage, int requestedSize) {
        return listScoped(null, aggregateType, search, createdFrom, createdTo,
                requestedPage, requestedSize, true);
    }

    private PagedResult<AuditLogResponse> listScoped(UUID tenantId, String aggregateType, String search,
            LocalDate createdFrom, LocalDate createdTo, int requestedPage, int requestedSize,
            boolean platformScope) {
        int page = Math.max(DEFAULT_PAGE, requestedPage);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(requestedSize, MAX_SIZE);
        Pageable pageable = PageRequest.of(page, size);
        String normalizedSearch = search == null ? null : search.trim().toLowerCase(Locale.ROOT);
        if (normalizedSearch != null && normalizedSearch.isBlank()) {
            normalizedSearch = null;
        }
        Instant createdFromInclusive = createdFrom == null
                ? null
                : createdFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant createdToExclusive = createdTo == null
                ? null
                : createdTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Page<AuditLog> logs;
        if (normalizedSearch == null && createdFromInclusive == null && createdToExclusive == null) {
            if (platformScope) {
                logs = aggregateType != null && !aggregateType.isBlank()
                        ? auditLogRepository.findByTenantIdIsNullAndAggregateTypeOrderByCreatedAtDesc(
                                aggregateType, pageable)
                        : auditLogRepository.findByTenantIdIsNullOrderByCreatedAtDesc(pageable);
            } else {
                logs = aggregateType != null && !aggregateType.isBlank()
                        ? auditLogRepository.findByTenantIdAndAggregateTypeOrderByCreatedAtDesc(
                                tenantId, aggregateType, pageable)
                        : auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
            }
        } else {
            logs = platformScope
                    ? auditLogRepository.findForPlatform(
                            aggregateType != null && !aggregateType.isBlank() ? aggregateType : null,
                            normalizedSearch, createdFromInclusive, createdToExclusive, pageable)
                    : auditLogRepository.findForTenant(tenantId,
                            aggregateType != null && !aggregateType.isBlank() ? aggregateType : null,
                            normalizedSearch, createdFromInclusive, createdToExclusive, pageable);
        }
        return new PagedResult<>(logs.map(AuditLogService::toResponse).getContent(),
                new PageMeta(logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages(),
                        logs.isFirst(), logs.isLast(), logs.hasNext(), logs.hasPrevious()));
    }

    /**
     * Newest audit records of one tenant (no total count, no paging metadata) for
     * compact feeds. {@code size} is clamped to {@code 1..MAX_SIZE}. A null tenant
     * is rejected: a derived query would otherwise match the platform-level rows
     * whose {@code tenant_id} is NULL.
     */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> recent(UUID tenantId, int size) {
        Objects.requireNonNull(tenantId, "tenantId");
        int bounded = Math.max(1, Math.min(size, MAX_SIZE));
        return auditLogRepository
                .findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenantId, PageRequest.of(DEFAULT_PAGE, bounded))
                .stream().map(AuditLogService::toResponse).toList();
    }

    private static AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getPublicId(),
                auditLog.getActorUserId(),
                auditLog.getAggregateType(),
                auditLog.getAggregateId(),
                auditLog.getResourceName(),
                auditLog.getResourceCode(),
                auditLog.getAction(),
                auditLog.getSummary(),
                auditLog.getCreatedAt());
    }
}
