package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionWindowOutsideSubscriptionException extends DomainException {

    public SessionWindowOutsideSubscriptionException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, SessionConstants.SESSION_WINDOW_OUTSIDE_SUBSCRIPTION,
                null, SessionConstants.SESSION_WINDOW_OUTSIDE_SUBSCRIPTION_FRIENDLY);
    }
}
