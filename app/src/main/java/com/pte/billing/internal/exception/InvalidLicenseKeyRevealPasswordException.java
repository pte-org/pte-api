package com.pte.billing.internal.exception;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when the caller's re-entered password fails the reveal-license-key step-up check. */
public class InvalidLicenseKeyRevealPasswordException extends DomainException {

    public InvalidLicenseKeyRevealPasswordException() {
        super(HttpStatus.UNAUTHORIZED, BillingConstants.LICENSE_KEY_REVEAL_INVALID_PASSWORD);
    }
}
