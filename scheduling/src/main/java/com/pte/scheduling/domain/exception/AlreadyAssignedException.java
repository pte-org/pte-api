package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

public class AlreadyAssignedException extends DomainException {

    public AlreadyAssignedException() {
        super(HttpStatus.CONFLICT, SchedulingConstants.ALREADY_ASSIGNED);
    }
}
