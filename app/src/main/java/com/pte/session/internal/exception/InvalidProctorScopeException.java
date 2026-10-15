package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidProctorScopeException extends DomainException {

    public InvalidProctorScopeException() {
        super(HttpStatus.BAD_REQUEST, SessionConstants.INVALID_PROCTOR_SCOPE);
    }
}
