package com.pte.billing.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A PayOS webhook was malformed or failed authenticity/business checks. */
public class PaymentWebhookException extends DomainException {

    public PaymentWebhookException(String code) {
        super(HttpStatus.BAD_REQUEST, code);
    }
}
