package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionCapacityInvalidException extends DomainException {

    public SessionCapacityInvalidException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, SessionConstants.CAPACITY_POSITIVE);
    }
}
