package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TenantNameAlreadyUsedException extends DomainException {

    public TenantNameAlreadyUsedException() {
        super(HttpStatus.CONFLICT, AdminConstants.TENANT_NAME_ALREADY_USED);
    }
}
