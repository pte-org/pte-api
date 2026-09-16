package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class LecturerAssignmentNotFoundException extends DomainException {

    public LecturerAssignmentNotFoundException() {
        super(HttpStatus.NOT_FOUND, EnrollmentConstants.LECTURER_ASSIGNMENT_NOT_FOUND);
    }
}
