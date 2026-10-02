package com.pte.proctoring.internal.exception;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidSecurityAuditLimitException extends DomainException {

    public InvalidSecurityAuditLimitException() {
        super(HttpStatus.BAD_REQUEST, ProctorConstants.SECURITY_AUDIT_LIMIT_INVALID, null,
                ProctorConstants.SECURITY_AUDIT_LIMIT_INVALID_MESSAGE);
    }
}
