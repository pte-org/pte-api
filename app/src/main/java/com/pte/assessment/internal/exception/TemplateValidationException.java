package com.pte.assessment.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A template aggregate violates a structural rule required for activation. */
public class TemplateValidationException extends DomainException {

    public TemplateValidationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
