package com.pte.scoring.domain.exception;

import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Asked to score a task type this phase has no rule-based scorer for (fail fast, never a silent wrong score). */
public class UnsupportedTaskTypeException extends DomainException {

    public UnsupportedTaskTypeException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "UNSUPPORTED_TASK_TYPE");
    }
}
