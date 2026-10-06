package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Fails closed when a session row violates the billing tenant scope. */
public class SubscriptionRevocationScopeConflictException extends DomainException {

    public SubscriptionRevocationScopeConflictException() {
        super(HttpStatus.CONFLICT,
                SessionConstants.SUBSCRIPTION_REVOCATION_SCOPE_CONFLICT,
                null,
                SessionConstants.SUBSCRIPTION_REVOCATION_SCOPE_CONFLICT_FRIENDLY);
    }
}
