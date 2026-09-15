package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ProgramHasActiveClassesException extends DomainException {

    public ProgramHasActiveClassesException() {
        super(HttpStatus.CONFLICT, EnrollmentConstants.PROGRAM_HAS_ACTIVE_CLASSES);
    }
}
