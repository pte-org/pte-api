package com.pte.practice.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Stable practice-session lifecycle failure. */
public class PracticeSessionException extends DomainException {

    public PracticeSessionException(HttpStatus status, String code, String userMessage) {
        super(status, code, null, userMessage);
    }
}
