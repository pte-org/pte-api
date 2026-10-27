package com.pte.billing.internal.exception.order;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A business-rule failure while creating or transitioning a billing order. */
public class OrderException extends DomainException {

    public OrderException(HttpStatus status, String code) {
        super(status, code);
    }
}
