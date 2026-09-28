package com.pte.proctoring.internal.exception;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidSecurityAuditCursorException extends DomainException {

    public InvalidSecurityAuditCursorException() {
        super(HttpStatus.BAD_REQUEST, ProctorConstants.SECURITY_AUDIT_CURSOR_INVALID, null,
                ProctorConstants.SECURITY_AUDIT_CURSOR_INVALID_MESSAGE);
    }
}
