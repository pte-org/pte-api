package com.pte.billing.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A plan family rule failed after request binding. */
public class PlanValidationException extends DomainException {

    public PlanValidationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
