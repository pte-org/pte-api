package com.pte.proctoring.internal.exception;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * {@code @PreAuthorize} doesn't enforce on {@code @MessageMapping} methods
 * under this module's STOMP configuration — role is checked explicitly instead
 * (see {@code ProctorStompController}).
 */
public class ProctorRoleRequiredException extends DomainException {

    public ProctorRoleRequiredException() {
        super(HttpStatus.FORBIDDEN, ProctorConstants.PROCTOR_ROLE_REQUIRED);
    }
}
