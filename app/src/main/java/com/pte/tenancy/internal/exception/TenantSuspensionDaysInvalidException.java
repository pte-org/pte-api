package com.pte.tenancy.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.tenancy.internal.constant.TenancyConstants;
import org.springframework.http.HttpStatus;

public class TenantSuspensionDaysInvalidException extends DomainException {

    public TenantSuspensionDaysInvalidException() {
        super(HttpStatus.BAD_REQUEST, TenancyConstants.TENANT_SUSPENSION_DAYS_INVALID);
    }
}
