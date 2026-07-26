package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

/** Student is not enrolled, or the session isn't open, for the requested attempt. */
public class NotEntitledException extends DomainException {

    public NotEntitledException() {
        super(HttpStatus.FORBIDDEN, SchedulingConstants.NOT_ENTITLED);
    }
}
