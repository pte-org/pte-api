package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AlreadyAttemptedException extends DomainException {

    public AlreadyAttemptedException() {
        super(HttpStatus.CONFLICT, AttemptConstants.ALREADY_ATTEMPTED);
    }
}
