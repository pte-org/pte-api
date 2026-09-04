package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** The guarded call to media for a presigned image URL failed at StartAttempt time. */
public class ImageResolutionFailedException extends DomainException {

    public ImageResolutionFailedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, ExamDeliveryConstants.IMAGE_RESOLUTION_FAILED);
    }
}
