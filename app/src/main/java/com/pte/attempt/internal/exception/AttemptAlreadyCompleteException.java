package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AttemptAlreadyCompleteException extends DomainException {

    public AttemptAlreadyCompleteException() {
        super(HttpStatus.CONFLICT, AttemptConstants.ATTEMPT_ALREADY_COMPLETE);
    }
}
