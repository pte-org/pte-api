package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** A LISTENING-section pinned item has no {@code audioPromptRef} — an authoring data problem, not a client error. */
public class MissingAudioPromptException extends DomainException {

    public MissingAudioPromptException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ExamDeliveryConstants.MISSING_AUDIO_PROMPT);
    }
}
