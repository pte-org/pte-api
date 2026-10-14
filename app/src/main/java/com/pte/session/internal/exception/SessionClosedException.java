package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The session is closed or cancelled, or its scheduled {@code closesAt} has passed. */
public class SessionClosedException extends DomainException {

    public SessionClosedException() {
        super(HttpStatus.FORBIDDEN, SessionConstants.SESSION_CLOSED, null,
                SessionConstants.SESSION_CLOSED_FRIENDLY);
    }
}
