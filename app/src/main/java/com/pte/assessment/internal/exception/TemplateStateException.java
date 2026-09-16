package com.pte.assessment.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A template lifecycle or immutability operation is not allowed. */
public class TemplateStateException extends DomainException {

    public TemplateStateException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
