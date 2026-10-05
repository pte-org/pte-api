package com.pte.billing.internal.exception;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Approval-time tax-code collision translated into the billing application contract. */
public class RequestedTaxCodeAlreadyUsedException extends DomainException {

    public RequestedTaxCodeAlreadyUsedException() {
        super(HttpStatus.CONFLICT, BillingConstants.TENANT_TAX_CODE_ALREADY_USED);
    }
}
