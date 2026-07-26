package com.pte.authoring.domain.exception;

import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * A task-type-specific required field was missing/invalid. The message carries the
 * specific reason code (e.g. which field), built by the validation helper.
 */
public class QuestionValidationException extends DomainException {

    public QuestionValidationException(String code) {
        super(HttpStatus.BAD_REQUEST, code);
    }
}
