package com.pte.tenancy.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.tenancy.internal.constant.TenancyConstants;
import org.springframework.http.HttpStatus;

/** The suspension preview no longer describes the live subscription set. */
public class TenantSuspensionImpactStaleException extends DomainException {

    public TenantSuspensionImpactStaleException() {
        super(HttpStatus.CONFLICT, TenancyConstants.TENANT_SUSPENSION_IMPACT_STALE);
    }
}
