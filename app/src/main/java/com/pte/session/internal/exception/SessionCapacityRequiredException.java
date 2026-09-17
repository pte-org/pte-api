package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionCapacityRequiredException extends DomainException {

    public SessionCapacityRequiredException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, SessionConstants.CAPACITY_REQUIRED);
    }
}
