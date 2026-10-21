package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionNotClosedForExaminerAssignmentException extends DomainException {

    public SessionNotClosedForExaminerAssignmentException() {
        super(HttpStatus.CONFLICT, SessionConstants.EXAMINER_ASSIGNMENT_REQUIRES_CLOSED_SESSION,
                null, SessionConstants.EXAMINER_ASSIGNMENT_REQUIRES_CLOSED_SESSION_MESSAGE);
    }
}
