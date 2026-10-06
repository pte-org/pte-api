package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The exam code a student typed is blank or longer than any generated code. */
public class InvalidSessionCodeException extends DomainException {

    public InvalidSessionCodeException() {
        super(HttpStatus.BAD_REQUEST, SessionConstants.INVALID_SESSION_CODE);
    }
}
