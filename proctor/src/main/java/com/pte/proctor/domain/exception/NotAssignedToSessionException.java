package com.pte.proctor.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.proctor.constant.ProctorConstants;
import org.springframework.http.HttpStatus;

/** scheduling confirmed the calling proctor is NOT assigned to the requested session — a clean denial, not an infra failure. */
public class NotAssignedToSessionException extends DomainException {

    public NotAssignedToSessionException() {
        super(HttpStatus.FORBIDDEN, ProctorConstants.NOT_ASSIGNED_TO_SESSION);
    }
}
