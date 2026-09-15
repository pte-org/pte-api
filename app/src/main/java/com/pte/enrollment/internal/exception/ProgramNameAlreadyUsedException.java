package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ProgramNameAlreadyUsedException extends DomainException {

    public ProgramNameAlreadyUsedException() {
        super(HttpStatus.CONFLICT, EnrollmentConstants.PROGRAM_NAME_ALREADY_USED);
    }
}
