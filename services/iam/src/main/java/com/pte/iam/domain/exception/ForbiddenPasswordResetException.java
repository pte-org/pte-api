package com.pte.iam.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.iam.constant.IamConstants;
import org.springframework.http.HttpStatus;

/**
 * A tenant-scoped caller (HOST_ADMIN) tried to reset the password of a
 * same-tenant user whose role is outside {STUDENT, PROCTOR} — e.g. a fellow
 * HOST_ADMIN/HOST_AUTHOR. Host-assisted reset is scoped to rescuing a
 * locked-out Student/Proctor, not peer account recovery.
 */
public class ForbiddenPasswordResetException extends DomainException {

    public ForbiddenPasswordResetException() {
        super(HttpStatus.FORBIDDEN, IamConstants.FORBIDDEN_PASSWORD_RESET);
    }
}
