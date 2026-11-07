package com.pte.itembank.internal.generation.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A daily quota or concurrency cap that stops a new job. */
public class GenerationLimitException extends DomainException {

    public GenerationLimitException(String code, String userMessage) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, null, userMessage);
    }
}
