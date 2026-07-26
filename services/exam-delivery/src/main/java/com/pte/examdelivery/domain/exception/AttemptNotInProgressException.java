package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

public class AttemptNotInProgressException extends DomainException {

    public AttemptNotInProgressException() {
        super(HttpStatus.CONFLICT, ExamDeliveryConstants.ATTEMPT_NOT_IN_PROGRESS);
    }
}
