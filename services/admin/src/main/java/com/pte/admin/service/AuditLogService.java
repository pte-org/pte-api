package com.pte.admin.service;

import com.pte.admin.domain.AuditLog;
import com.pte.admin.dto.response.AuditLogResponse;
import com.pte.admin.mapper.AuditLogMapper;
import com.pte.admin.repository.AuditLogRepository;
import com.pte.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Append-only audit ledger for Program/Class/ClassMembership/
 * LecturerAssignment/ProgramCoordinatorAssignment mutations — a retrofit
 * concern called from {@code ProgramService}/{@code ClassService}/
 * {@code AssignmentService}'s existing write methods, beside their existing
 * {@code outboxWriter.write(...)} call, never as a replacement for it.
 *
 * <p>Deliberately does NOT cover {@code scheduling}'s bulk-enrollment-for-
 * Program activity (Phases 10/11) — {@code ExamSession} carries no
 * {@code programPublicId} today, so there's no clean way to attribute a
 * {@code StudentEnrolled} event back to the Program that triggered it
 * without a new cross-service tag this plan doesn't otherwise need. The FE
 * audit log view surfaces this as an explicit, visible scope note rather
 * than silently looking incomplete.
 *
 * <p>{@code record(...)} is deliberately plain {@code @Transactional}
 * (Spring's default propagation is {@code REQUIRED}) so it always joins
 * whichever transaction is already open at the call site — every caller
 * here is itself an {@code @Transactional} write method, so the audit row
 * is committed or rolled back atomically with the entity change and the
 * outbox write, never left orphaned by a later failure in the same method.
 */
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
                ? auditLogRepository.findByTenantIdAndAggregateTypeOrderByCreatedAtDesc(caller.tenantId(), aggregateType)
                : auditLogRepository.findByTenantIdOrderByCreatedAtDesc(caller.tenantId());
        return logs.stream().map(AuditLogMapper::toResponse).toList();
    }
}
