package com.pte.proctoring.internal.exception;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TargetAttemptInvalidException extends DomainException {

    public TargetAttemptInvalidException() {
        super(HttpStatus.BAD_REQUEST, ProctorConstants.TARGET_ATTEMPT_INVALID);
    }
}
