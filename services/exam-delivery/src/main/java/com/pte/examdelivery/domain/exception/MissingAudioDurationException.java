package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/**
 * One of the 5 audio-prompt Speaking task types is being pinned, but the
 * resolved media response carries no duration (e.g. a legacy media object
 * uploaded before duration was computed, or a null {@code audioPromptRef}
 * where authoring validation should have guaranteed one) — an authoring/
 * data-completeness problem, not a call failure like
 * {@link AudioResolutionFailedException}'s 503, so 422 like the sibling
 * {@link MissingAudioPromptException} (plans/phat-speaking-dynamic-prep-timing).
 */
public class MissingAudioDurationException extends DomainException {

    public MissingAudioDurationException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ExamDeliveryConstants.MISSING_AUDIO_DURATION);
    }
}
