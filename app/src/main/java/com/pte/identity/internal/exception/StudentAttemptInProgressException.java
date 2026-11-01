package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A Student is taking an exam, so their credential must not change underneath them. */
public class StudentAttemptInProgressException extends DomainException {

    public StudentAttemptInProgressException() {
        super(HttpStatus.CONFLICT, IdentityConstants.STUDENT_ATTEMPT_IN_PROGRESS);
    }
}
