package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AlreadyAssignedException extends DomainException {

    public AlreadyAssignedException() {
        super(HttpStatus.CONFLICT, SessionConstants.ALREADY_ASSIGNED);
    }
}
