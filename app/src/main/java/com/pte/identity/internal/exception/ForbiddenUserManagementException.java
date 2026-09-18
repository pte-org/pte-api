package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Caller tried to read or manage a user outside their role hierarchy. */
public class ForbiddenUserManagementException extends DomainException {

    public ForbiddenUserManagementException() {
        super(HttpStatus.FORBIDDEN, IdentityConstants.FORBIDDEN_USER_MANAGEMENT);
    }
}
