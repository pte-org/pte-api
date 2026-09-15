package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AttemptNotFoundException extends DomainException {

    public AttemptNotFoundException() {
        super(HttpStatus.NOT_FOUND, AttemptConstants.ATTEMPT_NOT_FOUND);
    }
}
