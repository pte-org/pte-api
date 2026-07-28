package com.pte.scoring.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scoring.constant.ScoringConstants;
import org.springframework.http.HttpStatus;

/** The host review queue only exposes the pending-review state with bounded pagination. */
public class InvalidReviewQueryException extends DomainException {

    public InvalidReviewQueryException() {
        super(HttpStatus.BAD_REQUEST, ScoringConstants.INVALID_REVIEW_QUERY);
    }
}
