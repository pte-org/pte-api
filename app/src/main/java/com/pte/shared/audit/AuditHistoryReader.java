package com.pte.shared.audit;

import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Tenant-scoped read of one aggregate's audit trail; the counterpart of {@link AuditLogChangeWriter}. */
@Service
public class AuditHistoryReader {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AuditLogRepository auditLogRepository;

    public AuditHistoryReader(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public PagedResult<AuditHistoryEntry> history(UUID tenantId, String aggregateType, String aggregateId,
            String action, int requestedPage, int requestedSize) {
        int page = Math.max(requestedPage, 0);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(requestedSize, MAX_SIZE);
        Page<AuditLog> logs = auditLogRepository
                .findByTenantIdAndAggregateTypeAndAggregateIdAndActionOrderByCreatedAtDescIdDesc(
                        tenantId, aggregateType, aggregateId, action, PageRequest.of(page, size));
        return new PagedResult<>(logs.map(AuditHistoryReader::toEntry).getContent(),
                new PageMeta(logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages(),
                        logs.isFirst(), logs.isLast(), logs.hasNext(), logs.hasPrevious()));
    }

    /** The whole trail of an aggregate across its actions, newest first. */
    @Transactional(readOnly = true)
    public PagedResult<AuditHistoryEntry> history(UUID tenantId, String aggregateType, String aggregateId,
            int requestedPage, int requestedSize) {
        int page = Math.max(requestedPage, 0);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(requestedSize, MAX_SIZE);
        Page<AuditLog> logs = auditLogRepository
                .findByTenantIdAndAggregateTypeAndAggregateIdOrderByCreatedAtDescIdDesc(
                        tenantId, aggregateType, aggregateId, PageRequest.of(page, size));
        return new PagedResult<>(logs.map(AuditHistoryReader::toEntry).getContent(),
                new PageMeta(logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages(),
                        logs.isFirst(), logs.isLast(), logs.hasNext(), logs.hasPrevious()));
    }

    private static AuditHistoryEntry toEntry(AuditLog log) {
        return new AuditHistoryEntry(log.getPublicId(), log.getActorUserId(), log.getActorRole(),
                log.getRequestPublicId(), log.getSummary(), log.getBeforeState(), log.getAfterState(),
                log.getCreatedAt());
    }
}
