package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

public class TaskTimingNotConfiguredException extends DomainException {

    public TaskTimingNotConfiguredException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ExamDeliveryConstants.TASK_TIMING_NOT_CONFIGURED);
    }
}
