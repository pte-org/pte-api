package com.pte.shared.audit;

import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.internal.constant.AuditLogConstants;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit rows that carry actor role, request id and before/after state. Used where the audit trail is
 * the history source of an endpoint (attendance history, source-selection audits, incident history).
 */
@Service
public class AuditLogChangeWriter {

    private final AuditLogRepository auditLogRepository;

    public AuditLogChangeWriter(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(CurrentUser caller, AuditChange change) {
        AuditLog auditLog = new AuditLog();
        auditLog.setTenantId(caller.tenantId());
        auditLog.setActorUserId(caller.userId());
        auditLog.setActorRole(String.join(AuditLogConstants.ROLE_SEPARATOR, caller.roles()));
        auditLog.setAggregateType(change.aggregateType());
        auditLog.setAggregateId(change.aggregateId());
        auditLog.setAction(change.action());
        auditLog.setSummary(change.summary());
        auditLog.setRequestPublicId(change.requestPublicId());
        auditLog.setBeforeState(change.beforeStateJson());
        auditLog.setAfterState(change.afterStateJson());
        auditLogRepository.save(auditLog);
    }
}
