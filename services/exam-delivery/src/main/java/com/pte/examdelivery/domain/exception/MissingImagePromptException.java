package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** A DESCRIBE_IMAGE pinned item has no {@code imagePromptRef} — an authoring data problem, not a client error. */
public class MissingImagePromptException extends DomainException {

    public MissingImagePromptException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ExamDeliveryConstants.MISSING_IMAGE_PROMPT);
    }
}
