package com.pte.proctoring.internal.exception;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SecurityAuditPolicyUnresolvedException extends DomainException {

    public SecurityAuditPolicyUnresolvedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ProctorConstants.SECURITY_AUDIT_POLICY_UNRESOLVED, null,
                ProctorConstants.SECURITY_AUDIT_POLICY_UNRESOLVED_MESSAGE);
    }
}
