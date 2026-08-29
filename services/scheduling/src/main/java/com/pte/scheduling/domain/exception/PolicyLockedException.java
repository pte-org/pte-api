package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

/** Session has passed the pre-open state — {@code ExamPolicy} is locked, regardless of attempt count. */
public class PolicyLockedException extends DomainException {

    public PolicyLockedException() {
        super(HttpStatus.CONFLICT, SchedulingConstants.POLICY_LOCKED);
    }
}
