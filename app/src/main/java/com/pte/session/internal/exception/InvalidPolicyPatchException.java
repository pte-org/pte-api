package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** {@code replayPolicyType=LIMITED} was patched without a {@code replayPolicyLimit}, or vice versa. */
public class InvalidPolicyPatchException extends DomainException {

    public InvalidPolicyPatchException() {
        super(HttpStatus.BAD_REQUEST, SessionConstants.INVALID_POLICY_PATCH);
    }
}
