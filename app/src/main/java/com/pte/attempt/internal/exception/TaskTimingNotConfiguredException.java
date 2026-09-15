package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class TaskTimingNotConfiguredException extends DomainException {

    public TaskTimingNotConfiguredException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, AttemptConstants.TASK_TIMING_NOT_CONFIGURED);
    }
}
