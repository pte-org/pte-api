package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

public class InvalidSessionWindowException extends DomainException {

    public InvalidSessionWindowException() {
        super(HttpStatus.BAD_REQUEST, SchedulingConstants.INVALID_SESSION_WINDOW);
    }
}
