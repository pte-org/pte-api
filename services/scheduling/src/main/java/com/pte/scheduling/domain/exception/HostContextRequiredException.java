package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

/** A platform-level (non-tenant) caller attempted a host-scoped scheduling action. */
public class HostContextRequiredException extends DomainException {

    public HostContextRequiredException() {
        super(HttpStatus.FORBIDDEN, SchedulingConstants.HOST_CONTEXT_REQUIRED);
    }
}
