package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class OrganizationNotFoundException extends DomainException {

    public OrganizationNotFoundException() {
        super(HttpStatus.NOT_FOUND, EnrollmentConstants.ORGANIZATION_NOT_FOUND);
    }
}
