package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

public class AttemptNotFoundException extends DomainException {

    public AttemptNotFoundException() {
        super(HttpStatus.NOT_FOUND, ExamDeliveryConstants.ATTEMPT_NOT_FOUND);
    }
}
