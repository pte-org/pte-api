package com.pte.billing.internal.service.license;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.license.LicenseCodeException;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;
import com.pte.tenancy.TenancyService;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.UUID;

/** Caller guards and bearer-code normalization shared by every license-code operation. */
class LicenseCodeAccessPolicy {

    private final AuditLogService auditLogService;
    private final TenancyService tenancyService;

    LicenseCodeAccessPolicy(AuditLogService auditLogService, TenancyService tenancyService) {
        this.auditLogService = auditLogService;
        this.tenancyService = tenancyService;
    }

    UUID requireHostTenant(CurrentUser caller) {
        if (caller == null || caller.userId() == null || !caller.hasRole("HOST_ADMIN")) {
            throw new LicenseCodeException(HttpStatus.FORBIDDEN, BillingConstants.LICENSE_CODE_HOST_ADMIN_REQUIRED);
        }
        if (caller.tenantId() == null) {
            throw invalid(BillingConstants.LICENSE_CODE_TENANT_REQUIRED);
        }
        tenancyService.assertTenantActive(caller.tenantId());
        return caller.tenantId();
    }

    void requirePlatformAdmin(CurrentUser caller) {
        if (caller == null || caller.userId() == null || !caller.isPlatformUser()
                || !caller.hasRole("PLATFORM_ADMIN")) {
            throw new LicenseCodeException(HttpStatus.FORBIDDEN, BillingConstants.LICENSE_CODE_PLATFORM_ADMIN_REQUIRED);
        }
    }

    void requirePlatformOperation(CurrentUser caller, PlatformOperation operation) {
        if (PlatformOperationPolicy.can(caller, operation)) {
            return;
        }
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, BillingConstants.LICENSE_CODE_AGGREGATE, "unknown",
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, BillingConstants.LICENSE_CODE_PLATFORM_OPERATION_REQUIRED);
        }
        throw new LicenseCodeException(HttpStatus.FORBIDDEN,
                BillingConstants.LICENSE_CODE_PLATFORM_OPERATION_REQUIRED);
    }

    static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw invalid(BillingConstants.LICENSE_CODE_REQUIRED);
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (normalized.indexOf('-') < 0 && normalized.length() == 8) {
            return normalized.substring(0, 4) + "-" + normalized.substring(4);
        }
        return normalized;
    }

    static LicenseCodeException invalid(String code) {
        return new LicenseCodeException(HttpStatus.UNPROCESSABLE_ENTITY, code);
    }
}
