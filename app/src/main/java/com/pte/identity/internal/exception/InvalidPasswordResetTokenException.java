package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The reset link is unknown, expired, already used, or its account can no longer sign in — never says which. */
public class InvalidPasswordResetTokenException extends DomainException {

    public InvalidPasswordResetTokenException() {
        super(HttpStatus.BAD_REQUEST, IdentityConstants.INVALID_PASSWORD_RESET_TOKEN);
    }
}
