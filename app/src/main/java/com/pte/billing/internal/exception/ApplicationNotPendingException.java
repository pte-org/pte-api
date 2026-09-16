package com.pte.billing.internal.exception;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ApplicationNotPendingException extends DomainException {

    public ApplicationNotPendingException() {
        super(HttpStatus.CONFLICT, BillingConstants.APPLICATION_NOT_PENDING);
    }
}
