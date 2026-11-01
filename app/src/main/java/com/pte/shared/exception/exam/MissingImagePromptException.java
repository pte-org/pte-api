package com.pte.shared.exception.exam;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A DESCRIBE_IMAGE exam item has no {@code imagePromptRef} — an authoring data problem, not a client error. */
public class MissingImagePromptException extends DomainException {

    public MissingImagePromptException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "MISSING_IMAGE_PROMPT");
    }
}
