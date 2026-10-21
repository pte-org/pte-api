package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The roster only changes before an exam runs or after it ends, never while it is being prepared or running. */
public class ExamAudienceLockedException extends DomainException {

    public ExamAudienceLockedException() {
        super(HttpStatus.CONFLICT, SessionConstants.EXAM_AUDIENCE_LOCKED, null,
                SessionConstants.EXAM_AUDIENCE_LOCKED_FRIENDLY);
    }
}
