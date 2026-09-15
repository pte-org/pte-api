package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Caller tried to grant a role they are not permitted to assign (e.g. a host creating a platform user). */
public class ForbiddenRoleAssignmentException extends DomainException {

    public ForbiddenRoleAssignmentException() {
        super(HttpStatus.FORBIDDEN, IdentityConstants.FORBIDDEN_ROLE_ASSIGNMENT);
    }
}
