package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Tried to unassign a Class that was never assigned to this session. */
public class ClassAssignmentNotFoundException extends DomainException {

    public ClassAssignmentNotFoundException() {
        super(HttpStatus.NOT_FOUND, SessionConstants.CLASS_ASSIGNMENT_NOT_FOUND);
    }
}
