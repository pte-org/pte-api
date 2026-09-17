package com.pte.billing.internal.exception;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TenantApplicationNotFoundException extends DomainException {

    public TenantApplicationNotFoundException() {
        super(HttpStatus.NOT_FOUND, BillingConstants.TENANT_APPLICATION_NOT_FOUND);
    }
}
