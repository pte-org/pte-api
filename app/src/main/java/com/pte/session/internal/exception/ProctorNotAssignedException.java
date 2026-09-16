package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The calling proctor is not assigned to the requested session. */
public class ProctorNotAssignedException extends DomainException {

    public ProctorNotAssignedException() {
        super(HttpStatus.FORBIDDEN, SessionConstants.PROCTOR_NOT_ASSIGNED);
    }
}
