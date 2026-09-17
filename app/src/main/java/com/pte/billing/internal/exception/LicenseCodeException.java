package com.pte.billing.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Domain failure raised by activation-code issue, redeem, or revoke flows. */
public class LicenseCodeException extends DomainException {

    public LicenseCodeException(HttpStatus status, String code) {
        super(status, code);
    }

    public LicenseCodeException(HttpStatus status, String code, Throwable cause) {
        super(status, code);
        initCause(cause);
    }
}
