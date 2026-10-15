package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidTimezoneException extends DomainException {

    public InvalidTimezoneException() {
        super(HttpStatus.BAD_REQUEST, SessionConstants.INVALID_TIMEZONE);
    }
}
