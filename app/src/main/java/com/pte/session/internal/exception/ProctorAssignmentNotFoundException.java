package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ProctorAssignmentNotFoundException extends DomainException {

    public ProctorAssignmentNotFoundException() {
        super(HttpStatus.NOT_FOUND, SessionConstants.PROCTOR_ASSIGNMENT_NOT_FOUND);
    }
}
