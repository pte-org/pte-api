package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** Submission arrived after the server-computed response deadline; the task was auto-expired. */
public class ResponseWindowExpiredException extends DomainException {

    public ResponseWindowExpiredException() {
        super(HttpStatus.CONFLICT, ExamDeliveryConstants.RESPONSE_WINDOW_EXPIRED);
    }
}
