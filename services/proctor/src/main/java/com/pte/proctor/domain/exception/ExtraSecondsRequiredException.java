package com.pte.proctor.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.proctor.constant.ProctorConstants;
import org.springframework.http.HttpStatus;

/** {@code EXTEND_TIME} requires a positive {@code extraSeconds}. */
public class ExtraSecondsRequiredException extends DomainException {

    public ExtraSecondsRequiredException() {
        super(HttpStatus.BAD_REQUEST, ProctorConstants.EXTRA_SECONDS_REQUIRED);
    }
}
