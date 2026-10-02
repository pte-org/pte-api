package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionNotReadyToOpenException extends DomainException {

    public SessionNotReadyToOpenException() {
        super(HttpStatus.CONFLICT, SessionConstants.SESSION_NOT_READY_TO_OPEN, null,
                SessionConstants.SESSION_NOT_READY_TO_OPEN_FRIENDLY);
    }
}
