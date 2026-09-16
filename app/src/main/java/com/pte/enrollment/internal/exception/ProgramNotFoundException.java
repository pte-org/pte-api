package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ProgramNotFoundException extends DomainException {

    public ProgramNotFoundException() {
        super(HttpStatus.NOT_FOUND, EnrollmentConstants.PROGRAM_NOT_FOUND);
    }
}
