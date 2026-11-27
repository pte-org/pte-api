package com.pte.proctoring.internal.exception;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A student who has started or submitted the exam cannot be marked absent. */
public class AbsentAfterAttemptException extends DomainException {

    public AbsentAfterAttemptException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ProctorConstants.ABSENT_AFTER_ATTEMPT, null,
                ProctorConstants.ABSENT_AFTER_ATTEMPT_MESSAGE);
    }
}
