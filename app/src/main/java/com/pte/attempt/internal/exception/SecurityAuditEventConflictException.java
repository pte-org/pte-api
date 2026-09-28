package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SecurityAuditEventConflictException extends DomainException {

    public SecurityAuditEventConflictException() {
        super(HttpStatus.CONFLICT, AttemptConstants.SECURITY_AUDIT_EVENT_CONFLICT, null,
                AttemptConstants.SECURITY_AUDIT_EVENT_CONFLICT_MESSAGE);
    }
}
