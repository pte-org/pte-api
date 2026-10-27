package com.pte.scoring.internal.exception.examiner;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an examiner attempts to submit an unpermitted decision kind (e.g. LEGACY_IMPORTED).
 */
public class InvalidDecisionKindException extends DomainException {

    public InvalidDecisionKindException() {
        super(HttpStatus.BAD_REQUEST, ExaminerScoringConstants.INVALID_DECISION_KIND);
    }
}
