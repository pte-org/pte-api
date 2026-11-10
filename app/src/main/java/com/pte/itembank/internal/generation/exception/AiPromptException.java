package com.pte.itembank.internal.generation.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** An AI prompt version that cannot be read, changed or moved to the requested status; codes live in PromptVersionConstants. */
public class AiPromptException extends DomainException {

    public AiPromptException(HttpStatus status, String code, String userMessage) {
        super(status, code, null, userMessage);
    }
}
