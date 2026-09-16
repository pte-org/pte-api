package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Wrong email/password, or a suspended account attempting to log in. */
public class InvalidLoginException extends DomainException {

    public InvalidLoginException() {
        super(HttpStatus.UNAUTHORIZED, IdentityConstants.INVALID_LOGIN);
    }
}
