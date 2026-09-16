package com.pte.billing.internal.exception;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class PlanNotFoundException extends DomainException {

    public PlanNotFoundException() {
        super(HttpStatus.NOT_FOUND, BillingConstants.PLAN_NOT_FOUND);
    }
}
