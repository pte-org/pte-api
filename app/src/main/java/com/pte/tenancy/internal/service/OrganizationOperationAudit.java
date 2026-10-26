package com.pte.tenancy.internal.service;

import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;
import com.pte.tenancy.internal.constant.TenancyConstants;

import java.util.UUID;

/** Platform-operation guard and audit trail for organization governance; a null caller is a trusted internal call. */
class OrganizationOperationAudit {

    private final AuditLogService auditLogService;

    OrganizationOperationAudit(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    void require(CurrentUser caller, PlatformOperation operation, String action) {
        if (caller == null) {
            return;
        }
        if (PlatformOperationPolicy.can(caller, operation)) {
            return;
        }
        if (auditLogService != null) {
            auditLogService.recordFailure(caller, TenancyConstants.AGGREGATE_TENANT, "unknown",
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, action);
        }
        throw new org.springframework.security.access.AccessDeniedException(SharedConstants.ACCESS_DENIED);
    }

    void audit(CurrentUser caller, String action, UUID publicId, String summary) {
        if (auditLogService != null && caller != null && publicId != null) {
            auditLogService.record(caller, TenancyConstants.AGGREGATE_TENANT, publicId.toString(), action, summary);
        }
    }
}
