package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

public class SessionNotFoundException extends DomainException {

    public SessionNotFoundException() {
        super(HttpStatus.NOT_FOUND, SchedulingConstants.SESSION_NOT_FOUND);
    }
}
