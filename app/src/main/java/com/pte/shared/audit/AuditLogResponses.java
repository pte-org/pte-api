package com.pte.shared.audit;

import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.dto.AuditLogResponse;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;

/** Maps audit rows to list responses; shared by the tenant and the platform read paths. */
final class AuditLogResponses {

    private AuditLogResponses() {
    }

    static AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getPublicId(),
                auditLog.getActorUserId(),
                auditLog.getActorRole(),
                auditLog.getAggregateType(),
                auditLog.getAggregateId(),
                auditLog.getResourceName(),
                auditLog.getResourceCode(),
                auditLog.getAction(),
                auditLog.getSummary(),
                auditLog.getBeforeState() != null || auditLog.getAfterState() != null,
                auditLog.getCreatedAt());
    }

    static PagedResult<AuditLogResponse> toPage(Page<AuditLog> logs) {
        return new PagedResult<>(logs.map(AuditLogResponses::toResponse).getContent(),
                new PageMeta(logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages(),
                        logs.isFirst(), logs.isLast(), logs.hasNext(), logs.hasPrevious()));
    }
}
