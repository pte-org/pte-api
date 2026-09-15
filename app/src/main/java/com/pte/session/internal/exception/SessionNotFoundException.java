package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionNotFoundException extends DomainException {

    public SessionNotFoundException() {
        super(HttpStatus.NOT_FOUND, SessionConstants.SESSION_NOT_FOUND);
    }
}
