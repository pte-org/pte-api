package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

public class AlreadyEnrolledException extends DomainException {

    public AlreadyEnrolledException() {
        super(HttpStatus.CONFLICT, SchedulingConstants.ALREADY_ENROLLED);
    }
}
