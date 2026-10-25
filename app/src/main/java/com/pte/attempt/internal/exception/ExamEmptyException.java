package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The session's frozen exam has no items, so there is nothing to start. */
public class ExamEmptyException extends DomainException {

    public ExamEmptyException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, AttemptConstants.EXAM_EMPTY);
    }
}
