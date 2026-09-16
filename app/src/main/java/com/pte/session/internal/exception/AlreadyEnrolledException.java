package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AlreadyEnrolledException extends DomainException {

    public AlreadyEnrolledException() {
        super(HttpStatus.CONFLICT, SessionConstants.ALREADY_ENROLLED);
    }
}
