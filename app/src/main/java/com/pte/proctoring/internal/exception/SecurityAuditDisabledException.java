package com.pte.proctoring.internal.exception;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SecurityAuditDisabledException extends DomainException {

    public SecurityAuditDisabledException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ProctorConstants.SECURITY_AUDIT_DISABLED, null,
                ProctorConstants.SECURITY_AUDIT_DISABLED_MESSAGE);
    }
}
