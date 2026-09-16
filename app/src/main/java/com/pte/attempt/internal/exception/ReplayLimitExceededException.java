package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** {@code playCount} already reached the pinned (item-override or session-policy) replay limit for this task. */
public class ReplayLimitExceededException extends DomainException {

    public ReplayLimitExceededException() {
        super(HttpStatus.FORBIDDEN, AttemptConstants.REPLAY_LIMIT_EXCEEDED);
    }
}
