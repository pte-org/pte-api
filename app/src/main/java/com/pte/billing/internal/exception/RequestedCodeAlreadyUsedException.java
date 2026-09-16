package com.pte.billing.internal.exception;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class RequestedCodeAlreadyUsedException extends DomainException {

    public RequestedCodeAlreadyUsedException() {
        super(HttpStatus.CONFLICT, BillingConstants.REQUESTED_CODE_ALREADY_USED);
    }
}
