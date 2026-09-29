package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SecurityAuditDisabledException extends DomainException {

    public SecurityAuditDisabledException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, AttemptConstants.SECURITY_AUDIT_DISABLED, null,
                AttemptConstants.SECURITY_AUDIT_DISABLED_MESSAGE);
    }
}
