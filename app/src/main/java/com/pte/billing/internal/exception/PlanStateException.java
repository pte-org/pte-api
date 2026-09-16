package com.pte.billing.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A plan lifecycle transition or edit is not allowed in its current state. */
public class PlanStateException extends DomainException {

    public PlanStateException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
