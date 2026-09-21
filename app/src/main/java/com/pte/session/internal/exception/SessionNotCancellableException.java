package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionNotCancellableException extends DomainException {

    public SessionNotCancellableException() {
        super(HttpStatus.CONFLICT, SessionConstants.SESSION_NOT_CANCELLABLE, null,
                SessionConstants.SESSION_NOT_CANCELLABLE_FRIENDLY);
    }
}
