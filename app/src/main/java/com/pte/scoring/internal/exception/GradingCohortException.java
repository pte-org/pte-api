package com.pte.scoring.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class GradingCohortException extends DomainException {
    public GradingCohortException(HttpStatus status, String code, Object data, String message) {
        super(status, code, data, message, message);
    }
}
