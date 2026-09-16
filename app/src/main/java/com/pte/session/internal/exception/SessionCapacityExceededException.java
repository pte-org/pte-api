package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionCapacityExceededException extends DomainException {

    public SessionCapacityExceededException() {
        super(HttpStatus.CONFLICT, SessionConstants.SESSION_CAPACITY_EXCEEDED);
    }
}
