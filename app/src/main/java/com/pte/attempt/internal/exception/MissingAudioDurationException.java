package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * One of the 5 audio-prompt Speaking task types is being pinned, but the
 * resolved media response carries no duration (e.g. a legacy media object
 * uploaded before duration was computed) — an authoring/data-completeness
 * problem, not a call failure, so 422 like the sibling
 * {@link MissingAudioPromptException}.
 */
public class MissingAudioDurationException extends DomainException {

    public MissingAudioDurationException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, AttemptConstants.MISSING_AUDIO_DURATION);
    }
}
