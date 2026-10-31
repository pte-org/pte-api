package com.pte.assessment.internal.exception.content;

import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The frozen exam has no timing source for one of its task types. */
public class TaskTimingNotConfiguredException extends DomainException {

    public TaskTimingNotConfiguredException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, AssessmentConstants.TASK_TIMING_NOT_CONFIGURED);
    }
}
