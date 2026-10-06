package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionNotClosedForGradingCohortException extends DomainException {

    public SessionNotClosedForGradingCohortException() {
        super(HttpStatus.CONFLICT, SessionConstants.GRADING_COHORT_REQUIRES_CLOSED_SESSION,
                null, SessionConstants.GRADING_COHORT_REQUIRES_CLOSED_SESSION_MESSAGE);
    }
}
