package com.pte.proctoring.internal.exception;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ProctorSessionNotActiveException extends DomainException {

    public ProctorSessionNotActiveException() {
        super(HttpStatus.CONFLICT, ProctorConstants.PROCTOR_SESSION_NOT_ACTIVE);
    }
}
