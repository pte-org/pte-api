package com.pte.scoring.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A rubric version that cannot be read, created or moved to the requested status; codes live in RubricConstants. */
public class RubricVersionException extends DomainException {

    public RubricVersionException(HttpStatus status, String code) {
        super(status, code);
    }
}
