package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidSessionWindowException extends DomainException {

    public InvalidSessionWindowException() {
        super(HttpStatus.BAD_REQUEST, SessionConstants.INVALID_SESSION_WINDOW);
    }
}
