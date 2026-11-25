package com.pte.shared.audit.internal.exception;

import com.pte.shared.audit.internal.constant.AuditLogConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The audit entry does not exist or is not a platform-level entry. */
public class AuditLogNotFoundException extends DomainException {

    public AuditLogNotFoundException() {
        super(HttpStatus.NOT_FOUND, AuditLogConstants.AUDIT_LOG_NOT_FOUND, null,
                AuditLogConstants.AUDIT_LOG_NOT_FOUND_MESSAGE);
    }
}
