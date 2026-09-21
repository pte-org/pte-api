package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Manual roster mutations cannot bypass a generated exam's immutable audience. */
public class ExamAudienceLockedException extends DomainException {

    public ExamAudienceLockedException() {
        super(HttpStatus.CONFLICT, SessionConstants.EXAM_AUDIENCE_LOCKED, null,
                SessionConstants.EXAM_AUDIENCE_LOCKED_FRIENDLY);
    }
}
