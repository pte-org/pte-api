package com.pte.session.internal.exception.lifecycle;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionNotOpenToCloseException extends DomainException {

    public SessionNotOpenToCloseException() {
        super(HttpStatus.CONFLICT, SessionConstants.SESSION_NOT_OPEN_TO_CLOSE, null,
                SessionConstants.SESSION_NOT_OPEN_TO_CLOSE_FRIENDLY);
    }
}
