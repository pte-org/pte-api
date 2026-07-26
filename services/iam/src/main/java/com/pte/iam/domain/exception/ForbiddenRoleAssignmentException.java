package com.pte.iam.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.iam.constant.IamConstants;
import org.springframework.http.HttpStatus;

/** Caller tried to grant a role they are not permitted to assign (e.g. a host creating a platform user). */
public class ForbiddenRoleAssignmentException extends DomainException {

    public ForbiddenRoleAssignmentException() {
        super(HttpStatus.FORBIDDEN, IamConstants.FORBIDDEN_ROLE_ASSIGNMENT);
    }
}
