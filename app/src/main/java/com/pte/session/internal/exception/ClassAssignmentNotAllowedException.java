package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Assign/unassign attempted while the session is no longer SCHEDULED. */
public class ClassAssignmentNotAllowedException extends DomainException {

    public ClassAssignmentNotAllowedException() {
        super(HttpStatus.CONFLICT, SessionConstants.CLASS_ASSIGNMENT_NOT_ALLOWED);
    }
}
