package com.pte.scoring.domain.exception;

import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * The {@code status} filter on the review list endpoint didn't match any
 * {@code ScoringAnswerStatus} value. Handled explicitly here rather than left
 * to fall through to a raw enum-binding failure: {@code
 * MethodArgumentTypeMismatchException} isn't mapped anywhere in {@code
 * GlobalExceptionHandler}, so an unhandled enum mismatch would otherwise
 * surface as a 500, not a 400.
 */
public class InvalidAnswerStatusException extends DomainException {

    public InvalidAnswerStatusException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_ANSWER_STATUS");
    }
}
