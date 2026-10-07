package com.pte.practice.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Stable catalog/preflight failure returned before practice execution begins. */
public class PracticeCatalogException extends DomainException {

    public PracticeCatalogException(HttpStatus status, String code, String userMessage) {
        super(status, code, null, userMessage);
    }
}
