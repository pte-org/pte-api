package com.pte.shared.audit;

import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.dto.AuditLogResponse;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** In-transaction append and tenant-scoped read API for audit records. */
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
        AuditLog auditLog = new AuditLog();
        auditLog.setTenantId(caller.tenantId());
        auditLog.setActorUserId(caller.userId());
        auditLog.setAggregateType(aggregateType);
        auditLog.setAggregateId(aggregateId);
        auditLog.setAction(action);
        auditLog.setSummary(summary);
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public PagedResult<AuditLogResponse> list(CurrentUser caller, String aggregateType, int requestedPage,
            int requestedSize) {
        int page = Math.max(DEFAULT_PAGE, requestedPage);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(requestedSize, MAX_SIZE);
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = aggregateType != null && !aggregateType.isBlank()
                ? auditLogRepository.findByTenantIdAndAggregateTypeOrderByCreatedAtDesc(
                        caller.tenantId(), aggregateType, pageable)
                : auditLogRepository.findByTenantIdOrderByCreatedAtDesc(caller.tenantId(), pageable);
        return new PagedResult<>(logs.map(AuditLogService::toResponse).getContent(),
                new PageMeta(logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages(),
                        logs.isFirst(), logs.isLast(), logs.hasNext(), logs.hasPrevious()));
    }

    private static AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getPublicId(),
                auditLog.getActorUserId(),
                auditLog.getAggregateType(),
                auditLog.getAggregateId(),
                auditLog.getAction(),
                auditLog.getSummary(),
                auditLog.getCreatedAt());
    }
}
