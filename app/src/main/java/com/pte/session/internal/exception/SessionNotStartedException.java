package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The session isn't open yet, or the host opened it before its scheduled {@code opensAt}. */
public class SessionNotStartedException extends DomainException {

    public SessionNotStartedException() {
        super(HttpStatus.FORBIDDEN, SessionConstants.SESSION_NOT_STARTED, null,
                SessionConstants.SESSION_NOT_STARTED_FRIENDLY);
    }
}
