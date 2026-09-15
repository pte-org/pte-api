package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A LISTENING-section pinned item has no {@code audioPromptRef} — an authoring data problem, not a client error. */
public class MissingAudioPromptException extends DomainException {

    public MissingAudioPromptException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, AttemptConstants.MISSING_AUDIO_PROMPT);
    }
}
