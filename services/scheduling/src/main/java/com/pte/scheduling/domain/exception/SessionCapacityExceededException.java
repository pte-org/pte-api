package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

public class SessionCapacityExceededException extends DomainException {

    public SessionCapacityExceededException() {
        super(HttpStatus.CONFLICT, SchedulingConstants.SESSION_CAPACITY_EXCEEDED);
    }
}
