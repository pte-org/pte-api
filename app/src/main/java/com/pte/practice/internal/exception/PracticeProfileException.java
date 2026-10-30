package com.pte.practice.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Practice student profile lifecycle or validation failure. */
public class PracticeProfileException extends DomainException {

    public PracticeProfileException(HttpStatus status, String code, String userMessage) {
        super(status, code, null, userMessage);
    }
}
