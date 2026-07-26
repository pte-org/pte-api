package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** scheduling rejected the entitlement check: not enrolled, or session not open. */
public class NotEntitledException extends DomainException {

    public NotEntitledException() {
        super(HttpStatus.FORBIDDEN, ExamDeliveryConstants.NOT_ENTITLED);
    }
}
