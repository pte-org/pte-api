package com.pte.assessment.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class BlueprintValidationException extends DomainException {

    public BlueprintValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
