package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Deliberately does not distinguish a missing student from a foreign student. */
public class StudentNotFoundException extends DomainException {

    public StudentNotFoundException() {
        super(HttpStatus.NOT_FOUND, EnrollmentConstants.STUDENT_NOT_FOUND);
    }
}
