package com.pte.billing.internal.exception;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Direct service callers receive the same actionable validation class as HTTP callers. */
public class TenantApplicationValidationException extends DomainException {

    public TenantApplicationValidationException(String userMessage) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, BillingConstants.TENANT_APPLICATION_INVALID,
                null, userMessage);
    }
}
