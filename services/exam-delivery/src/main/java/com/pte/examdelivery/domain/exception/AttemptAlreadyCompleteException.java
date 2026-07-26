package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

public class AttemptAlreadyCompleteException extends DomainException {

    public AttemptAlreadyCompleteException() {
        super(HttpStatus.CONFLICT, ExamDeliveryConstants.ATTEMPT_ALREADY_COMPLETE);
    }
}
