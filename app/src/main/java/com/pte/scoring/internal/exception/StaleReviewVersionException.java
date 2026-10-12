package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when the expected review version in the score request does not match the server state. */
public class StaleReviewVersionException extends DomainException {

    public StaleReviewVersionException() {
        super(HttpStatus.CONFLICT, ExaminerScoringConstants.STALE_REVIEW_VERSION);
    }
}
