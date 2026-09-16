package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, IdentityConstants.USER_NOT_FOUND);
    }
}
