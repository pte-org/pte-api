package com.pte.tenancy.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.tenancy.internal.constant.TenancyConstants;
import org.springframework.http.HttpStatus;

/** Raised at the start of a tenant-owned write while the tenant is suspended. */
public class TenantSuspendedException extends DomainException {

    public TenantSuspendedException() {
        super(HttpStatus.FORBIDDEN, TenancyConstants.TENANT_SUSPENDED);
    }
}
