package com.pte.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for all domain exceptions across modules. Each module's own
 * {@code exception} package subclasses this. Carries the HTTP status the
 * global handler should map to, plus a machine-readable code.
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;

    protected DomainException(HttpStatus status, String code) {
        super(code);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
