package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A credential email cannot be sent when the target account has no email. */
public class UserEmailRequiredException extends DomainException {

    public UserEmailRequiredException() {
        super(HttpStatus.BAD_REQUEST, IdentityConstants.USER_EMAIL_REQUIRED);
    }
}
