package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class CoordinatorAlreadyAssignedException extends DomainException {

    public CoordinatorAlreadyAssignedException() {
        super(HttpStatus.CONFLICT, EnrollmentConstants.COORDINATOR_ALREADY_ASSIGNED);
    }
}
