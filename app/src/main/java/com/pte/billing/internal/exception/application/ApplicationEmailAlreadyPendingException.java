package com.pte.billing.internal.exception.application;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The same contact email already has an application waiting for review. */
public class ApplicationEmailAlreadyPendingException extends DomainException {

    public ApplicationEmailAlreadyPendingException() {
        super(HttpStatus.CONFLICT, BillingConstants.APPLICATION_EMAIL_ALREADY_PENDING);
    }
}
