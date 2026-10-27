package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The requested status action does not apply to this kind of account. */
public class UnsupportedUserStatusActionException extends DomainException {

    public UnsupportedUserStatusActionException() {
        super(HttpStatus.BAD_REQUEST, IdentityConstants.USER_STATUS_ACTION_UNSUPPORTED);
    }
}
