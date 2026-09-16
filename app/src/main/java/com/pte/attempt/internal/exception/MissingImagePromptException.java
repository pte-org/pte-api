package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A DESCRIBE_IMAGE pinned item has no {@code imagePromptRef} — an authoring data problem, not a client error. */
public class MissingImagePromptException extends DomainException {

    public MissingImagePromptException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, AttemptConstants.MISSING_IMAGE_PROMPT);
    }
}
