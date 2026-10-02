package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionSubscriptionNotFoundException extends DomainException {

    public SessionSubscriptionNotFoundException() {
        super(HttpStatus.NOT_FOUND, SessionConstants.SESSION_SUBSCRIPTION_NOT_FOUND,
                null, SessionConstants.SESSION_SUBSCRIPTION_NOT_FOUND_FRIENDLY);
    }
}
