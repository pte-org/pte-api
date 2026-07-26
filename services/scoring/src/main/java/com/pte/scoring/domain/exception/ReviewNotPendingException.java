package com.pte.scoring.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scoring.constant.ScoringConstants;
import org.springframework.http.HttpStatus;

/** The answer isn't in AI_SCORED_PENDING_REVIEW (already reviewed, or not yet AI-scored). */
public class ReviewNotPendingException extends DomainException {

    public ReviewNotPendingException() {
        super(HttpStatus.CONFLICT, ScoringConstants.REVIEW_NOT_PENDING);
    }
}
