package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Prevents a stale session subscription write after lock acquisition. */
public class SessionSubscriptionConflictException extends DomainException {

    public SessionSubscriptionConflictException() {
        super(HttpStatus.CONFLICT,
                SessionConstants.SESSION_SUBSCRIPTION_CHANGED,
                null,
                SessionConstants.SESSION_SUBSCRIPTION_CHANGED_FRIENDLY);
    }
}
