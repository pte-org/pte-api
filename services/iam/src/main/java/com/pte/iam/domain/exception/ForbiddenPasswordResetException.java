package com.pte.iam.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.iam.constant.IamConstants;
import org.springframework.http.HttpStatus;

/** HOST_ADMIN tried to reset a same-tenant user outside {STUDENT, PROCTOR} — host-assisted reset rescues locked-out accounts, not peer admins. */
public class ForbiddenPasswordResetException extends DomainException {

    public ForbiddenPasswordResetException() {
        super(HttpStatus.FORBIDDEN, IamConstants.FORBIDDEN_PASSWORD_RESET);
    }
}
