package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

/** A non-Proctor or non-tenant caller attempted a Proctor-scoped query. */
public class ProctorContextRequiredException extends DomainException {

    public ProctorContextRequiredException() {
        super(HttpStatus.FORBIDDEN, SchedulingConstants.PROCTOR_CONTEXT_REQUIRED);
    }
}
