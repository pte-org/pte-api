package com.pte.tenancy.internal.exception;

import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class OrganizationNotFoundException extends DomainException {

    public OrganizationNotFoundException() {
        super(HttpStatus.NOT_FOUND, TenancyConstants.ORGANIZATION_NOT_FOUND);
    }
}
