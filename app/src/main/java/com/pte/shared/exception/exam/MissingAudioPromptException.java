package com.pte.shared.exception.exam;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A LISTENING-section exam item has no {@code audioPromptRef} — an authoring data problem, not a client error. */
public class MissingAudioPromptException extends DomainException {

    public MissingAudioPromptException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "MISSING_AUDIO_PROMPT");
    }
}
