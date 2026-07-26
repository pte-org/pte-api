package com.pte.proctor.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.proctor.constant.ProctorConstants;
import org.springframework.http.HttpStatus;

public class ProctorSessionNotActiveException extends DomainException {

    public ProctorSessionNotActiveException() {
        super(HttpStatus.CONFLICT, ProctorConstants.PROCTOR_SESSION_NOT_ACTIVE);
    }
}
