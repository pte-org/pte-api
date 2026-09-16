package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyUsedException extends DomainException {

    public EmailAlreadyUsedException() {
        super(HttpStatus.CONFLICT, IdentityConstants.EMAIL_ALREADY_USED);
    }
}
