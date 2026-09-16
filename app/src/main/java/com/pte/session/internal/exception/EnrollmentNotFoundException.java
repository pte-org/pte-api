package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class EnrollmentNotFoundException extends DomainException {

    public EnrollmentNotFoundException() {
        super(HttpStatus.NOT_FOUND, SessionConstants.ENROLLMENT_NOT_FOUND);
    }
}
