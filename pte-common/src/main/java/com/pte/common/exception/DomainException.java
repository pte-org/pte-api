package com.pte.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for all domain exceptions across services. Each service subclasses
 * this in its own {@code domain/exception} package. Carries the HTTP status the
 * global handler should map to, plus a machine-readable code (from a service's
 * {@code *Constants}).
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
