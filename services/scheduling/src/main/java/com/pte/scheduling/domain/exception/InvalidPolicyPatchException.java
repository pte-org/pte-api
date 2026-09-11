package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

/** {@code replayPolicyType=LIMITED} was patched without a {@code replayPolicyLimit}, or vice versa. */
public class InvalidPolicyPatchException extends DomainException {

    public InvalidPolicyPatchException() {
        super(HttpStatus.BAD_REQUEST, SchedulingConstants.INVALID_POLICY_PATCH);
    }
}
