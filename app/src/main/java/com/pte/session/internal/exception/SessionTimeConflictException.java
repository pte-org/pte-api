package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class SessionTimeConflictException extends DomainException {

    public SessionTimeConflictException() {
        super(HttpStatus.CONFLICT, SessionConstants.SESSION_TIME_CONFLICT);
    }

    public SessionTimeConflictException(UUID conflictingSessionPublicId) {
        super(HttpStatus.CONFLICT,
                SessionConstants.SESSION_TIME_CONFLICT + ": "
                        + String.format(SessionConstants.SESSION_TIME_CONFLICT_DETAIL, conflictingSessionPublicId));
    }
}
