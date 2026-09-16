package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class StudentAlreadyInClassException extends DomainException {

    public StudentAlreadyInClassException() {
        super(HttpStatus.CONFLICT, EnrollmentConstants.STUDENT_ALREADY_IN_CLASS);
    }
}
