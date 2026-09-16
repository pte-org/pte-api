package com.pte.tenancy.internal.exception;

import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TenantNotFoundException extends DomainException {

    public TenantNotFoundException() {
        super(HttpStatus.NOT_FOUND, TenancyConstants.TENANT_NOT_FOUND);
    }
}
