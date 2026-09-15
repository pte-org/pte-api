package com.pte.tenancy.internal.exception;

import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TenantNameAlreadyUsedException extends DomainException {

    public TenantNameAlreadyUsedException() {
        super(HttpStatus.CONFLICT, TenancyConstants.TENANT_NAME_ALREADY_USED);
    }
}
