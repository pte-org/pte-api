package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

public class AlreadyAttemptedException extends DomainException {

    public AlreadyAttemptedException() {
        super(HttpStatus.CONFLICT, ExamDeliveryConstants.ALREADY_ATTEMPTED);
    }
}
