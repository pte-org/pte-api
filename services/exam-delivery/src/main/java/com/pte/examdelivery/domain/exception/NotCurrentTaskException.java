package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** Submission targeted an item that isn't the attempt's current task (tasks are served strictly in order). */
public class NotCurrentTaskException extends DomainException {

    public NotCurrentTaskException() {
        super(HttpStatus.CONFLICT, ExamDeliveryConstants.NOT_CURRENT_TASK);
    }
}
