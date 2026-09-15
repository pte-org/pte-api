package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class StudentClassNotFoundException extends DomainException {

    public StudentClassNotFoundException() {
        super(HttpStatus.NOT_FOUND, EnrollmentConstants.CLASS_NOT_FOUND);
    }
}
