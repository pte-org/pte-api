package com.pte.itembank.internal.generation.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The job's current state does not allow the request (already running, already finished). */
public class GenerationJobConflictException extends DomainException {

    public GenerationJobConflictException(String code, String userMessage) {
        super(HttpStatus.CONFLICT, code, null, userMessage);
    }
}
