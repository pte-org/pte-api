package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class OrganizationNameAlreadyUsedException extends DomainException {

    public OrganizationNameAlreadyUsedException() {
        super(HttpStatus.CONFLICT, AdminConstants.ORGANIZATION_NAME_ALREADY_USED);
    }
}
