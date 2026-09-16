package com.pte.tenancy.internal.exception;

import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class OrganizationNameAlreadyUsedException extends DomainException {

    public OrganizationNameAlreadyUsedException() {
        super(HttpStatus.CONFLICT, TenancyConstants.ORGANIZATION_NAME_ALREADY_USED);
    }
}
