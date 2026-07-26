package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/** DB unique constraint (attempt, pinned_item) rejected a concurrent duplicate submission. */
public class AnswerAlreadySubmittedException extends DomainException {

    public AnswerAlreadySubmittedException() {
        super(HttpStatus.CONFLICT, "ANSWER_ALREADY_SUBMITTED");
    }
}
