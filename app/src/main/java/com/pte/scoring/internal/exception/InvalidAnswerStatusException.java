package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * The {@code status} filter on the review list endpoint didn't match any
 * {@code ScoringAnswerStatus} value. Handled explicitly rather than left to
 * fall through to a raw enum-binding failure.
 */
public class InvalidAnswerStatusException extends DomainException {

    public InvalidAnswerStatusException() {
        super(HttpStatus.BAD_REQUEST, ScoringConstants.INVALID_ANSWER_STATUS);
    }
}
