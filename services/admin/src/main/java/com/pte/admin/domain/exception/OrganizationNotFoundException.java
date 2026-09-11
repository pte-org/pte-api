package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class OrganizationNotFoundException extends DomainException {

    public OrganizationNotFoundException() {
        super(HttpStatus.NOT_FOUND, AdminConstants.ORGANIZATION_NOT_FOUND);
    }
}
