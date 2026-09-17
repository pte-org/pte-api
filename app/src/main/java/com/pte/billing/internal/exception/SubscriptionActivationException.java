package com.pte.billing.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** An activation request or its generated persistence key cannot be accepted. */
public class SubscriptionActivationException extends DomainException {

    public SubscriptionActivationException(HttpStatus status, String code) {
        super(status, code);
    }

    public SubscriptionActivationException(HttpStatus status, String code, Throwable cause) {
        super(status, code);
        initCause(cause);
    }
}
