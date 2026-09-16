package com.pte.tenancy.internal.exception;

import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TenantCodeAlreadyUsedException extends DomainException {

    public TenantCodeAlreadyUsedException() {
        super(HttpStatus.CONFLICT, TenancyConstants.TENANT_CODE_ALREADY_USED);
    }
}
