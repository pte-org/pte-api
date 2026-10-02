package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Student accounts do not support the credential-email action. */
public class StudentCredentialEmailNotAllowedException extends DomainException {

    public StudentCredentialEmailNotAllowedException() {
        super(HttpStatus.FORBIDDEN, IdentityConstants.STUDENT_CREDENTIAL_EMAIL_NOT_ALLOWED);
    }
}
