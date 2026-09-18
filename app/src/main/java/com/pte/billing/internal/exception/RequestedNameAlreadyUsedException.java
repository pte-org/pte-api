package com.pte.billing.internal.exception;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class RequestedNameAlreadyUsedException extends DomainException {

    public RequestedNameAlreadyUsedException() {
        super(HttpStatus.CONFLICT, BillingConstants.TENANT_NAME_ALREADY_USED);
    }
}
