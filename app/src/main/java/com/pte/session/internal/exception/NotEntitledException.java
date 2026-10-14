package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Student is not enrolled in the requested session, or the session does not exist. */
public class NotEntitledException extends DomainException {

    public NotEntitledException() {
        super(HttpStatus.FORBIDDEN, SessionConstants.NOT_ENTITLED);
    }
}
