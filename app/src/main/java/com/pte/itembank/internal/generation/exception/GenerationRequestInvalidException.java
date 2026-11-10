package com.pte.itembank.internal.generation.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A submit request field the form should have prevented (task type, count, CEFR, pool, topic). */
public class GenerationRequestInvalidException extends DomainException {

    public GenerationRequestInvalidException(String code, String userMessage) {
        super(HttpStatus.BAD_REQUEST, code, null, userMessage);
    }
}
