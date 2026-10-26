package com.pte.billing.internal.exception.order;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** An upstream PayOS request or response could not be completed safely. */
public class PayOsException extends DomainException {

    public PayOsException(String code) {
        super(HttpStatus.BAD_GATEWAY, code);
    }

    public PayOsException(String code, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, code);
        initCause(cause);
    }
}
