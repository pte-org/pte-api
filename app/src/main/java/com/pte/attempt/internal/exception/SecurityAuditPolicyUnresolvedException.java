package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SecurityAuditPolicyUnresolvedException extends DomainException {

    public SecurityAuditPolicyUnresolvedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, AttemptConstants.SECURITY_AUDIT_POLICY_UNRESOLVED, null,
                AttemptConstants.SECURITY_AUDIT_POLICY_UNRESOLVED_MESSAGE);
    }
}
