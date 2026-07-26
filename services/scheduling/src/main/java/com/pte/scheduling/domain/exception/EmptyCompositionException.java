package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

public class EmptyCompositionException extends DomainException {

    public EmptyCompositionException() {
        super(HttpStatus.BAD_REQUEST, SchedulingConstants.EMPTY_COMPOSITION);
    }
}
