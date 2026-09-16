package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Asked to score a task type this module has no rule-based scorer for (fail fast, never a silent wrong score). */
public class UnsupportedTaskTypeException extends DomainException {

    public UnsupportedTaskTypeException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ScoringConstants.UNSUPPORTED_TASK_TYPE);
    }
}
