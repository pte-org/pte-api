package com.pte.tenancy.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.tenancy.internal.constant.TenancyConstants;
import org.springframework.http.HttpStatus;

public class TenantTaxCodeAlreadyUsedException extends DomainException {

    public TenantTaxCodeAlreadyUsedException() {
        super(HttpStatus.CONFLICT, TenancyConstants.TENANT_TAX_CODE_ALREADY_USED);
    }
}
