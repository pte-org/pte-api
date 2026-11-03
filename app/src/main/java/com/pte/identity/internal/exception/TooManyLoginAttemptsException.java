package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Too many wrong passwords for one account from one address: sign-in is paused for a while, even with the right password. */
public class TooManyLoginAttemptsException extends DomainException {

    public TooManyLoginAttemptsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, IdentityConstants.TOO_MANY_LOGIN_ATTEMPTS);
    }
}
