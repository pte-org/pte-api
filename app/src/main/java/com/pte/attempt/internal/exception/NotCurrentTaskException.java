package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Submission targeted an item that isn't the attempt's current task (tasks are served strictly in order). */
public class NotCurrentTaskException extends DomainException {

    public NotCurrentTaskException() {
        super(HttpStatus.CONFLICT, AttemptConstants.NOT_CURRENT_TASK);
    }
}
