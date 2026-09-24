package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class RetryLimitReachedException extends DomainException {

    public RetryLimitReachedException() {
        super(HttpStatus.CONFLICT, AttemptConstants.RETRY_LIMIT_REACHED);
    }
}
