package com.pte.admin.mapper;

import com.pte.admin.domain.AuditLog;
import com.pte.admin.dto.response.AuditLogResponse;

public final class AuditLogMapper {

    private AuditLogMapper() {
    }

    public static AuditLogResponse toResponse(AuditLog auditLog) {
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
