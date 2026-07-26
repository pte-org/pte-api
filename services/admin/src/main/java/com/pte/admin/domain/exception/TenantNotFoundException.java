package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TenantNotFoundException extends DomainException {

    public TenantNotFoundException() {
        super(HttpStatus.NOT_FOUND, AdminConstants.TENANT_NOT_FOUND);
    }
}
