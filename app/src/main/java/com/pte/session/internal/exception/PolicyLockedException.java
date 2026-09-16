package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Session has passed the pre-open state — {@code ExamPolicy} is locked, regardless of attempt count. */
public class PolicyLockedException extends DomainException {

    public PolicyLockedException() {
        super(HttpStatus.CONFLICT, SessionConstants.POLICY_LOCKED);
    }
}
