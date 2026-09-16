package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** HOST_ADMIN tried to reset a same-tenant user outside {STUDENT, PROCTOR} — host-assisted reset rescues locked-out accounts, not peer admins. */
public class ForbiddenPasswordResetException extends DomainException {

    public ForbiddenPasswordResetException() {
        super(HttpStatus.FORBIDDEN, IdentityConstants.FORBIDDEN_PASSWORD_RESET);
    }
}
