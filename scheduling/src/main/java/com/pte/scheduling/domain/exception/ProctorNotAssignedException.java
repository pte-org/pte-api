package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

/** The calling proctor is not assigned to the requested session. */
public class ProctorNotAssignedException extends DomainException {

    public ProctorNotAssignedException() {
        super(HttpStatus.FORBIDDEN, SchedulingConstants.PROCTOR_NOT_ASSIGNED);
    }
}
