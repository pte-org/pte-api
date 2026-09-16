package com.pte.shared.audit;

import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.dto.AuditLogResponse;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** In-transaction append and tenant-scoped read API for audit records. */
@Service
public class AuditLogService {

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
    public List<AuditLogResponse> list(CurrentUser caller, String aggregateType) {
        List<AuditLog> logs = aggregateType != null && !aggregateType.isBlank()
                ? auditLogRepository.findByTenantIdAndAggregateTypeOrderByCreatedAtDesc(
                        caller.tenantId(), aggregateType)
                : auditLogRepository.findByTenantIdOrderByCreatedAtDesc(caller.tenantId());
        return logs.stream().map(AuditLogService::toResponse).toList();
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
