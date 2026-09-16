package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Submission request shape (plain vs. encrypted) doesn't match the attempt's pinned {@code answerIntegrityLevel} — server-decided routing, never client-chosen. */
public class AnswerIntegrityLevelMismatchException extends DomainException {

    public AnswerIntegrityLevelMismatchException() {
        super(HttpStatus.CONFLICT, AttemptConstants.ANSWER_INTEGRITY_LEVEL_MISMATCH);
    }
}
