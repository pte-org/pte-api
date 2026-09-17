package com.pte.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for all domain exceptions across modules. Each module's own
 * {@code exception} package subclasses this. Carries the HTTP status the
 * global handler should map to, plus a machine-readable code.
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final Object data;

    protected DomainException(HttpStatus status, String code) {
        this(status, code, null);
    }

    /** For a subtype whose response body needs structured {@code data}, not just a message code. */
    protected DomainException(HttpStatus status, String code, Object data) {
        super(code);
        this.status = status;
        this.data = data;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object getData() {
        return data;
    }
}
