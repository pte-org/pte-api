package com.pte.billing.internal.exception;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SubscriptionNotFoundException extends DomainException {

    public SubscriptionNotFoundException() {
        super(HttpStatus.NOT_FOUND, BillingConstants.SUBSCRIPTION_NOT_FOUND);
    }
}
