package com.pte.proctoring.internal.exception;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SecurityAuditEventConflictException extends DomainException {

    public SecurityAuditEventConflictException() {
        super(HttpStatus.CONFLICT, ProctorConstants.SECURITY_AUDIT_EVENT_CONFLICT, null,
                ProctorConstants.SECURITY_AUDIT_EVENT_CONFLICT_MESSAGE);
    }
}
