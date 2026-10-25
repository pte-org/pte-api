package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** DB unique constraint (attempt, item) rejected a concurrent duplicate submission. */
public class AnswerAlreadySubmittedException extends DomainException {

    public AnswerAlreadySubmittedException() {
        super(HttpStatus.CONFLICT, AttemptConstants.ANSWER_ALREADY_SUBMITTED);
    }
}
