package com.pte.billing.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class PlanLifecycleException extends DomainException {
    public PlanLifecycleException(String code, String userMessage) {
        super(HttpStatus.CONFLICT, code, null, userMessage);
    }
}
