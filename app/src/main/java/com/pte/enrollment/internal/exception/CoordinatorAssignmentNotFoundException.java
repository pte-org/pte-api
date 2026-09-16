package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class CoordinatorAssignmentNotFoundException extends DomainException {

    public CoordinatorAssignmentNotFoundException() {
        super(HttpStatus.NOT_FOUND, EnrollmentConstants.COORDINATOR_ASSIGNMENT_NOT_FOUND);
    }
}
