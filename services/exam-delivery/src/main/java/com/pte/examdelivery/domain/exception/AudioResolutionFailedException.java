package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** The guarded call to media for a presigned audio URL failed at StartAttempt time. */
public class AudioResolutionFailedException extends DomainException {

    public AudioResolutionFailedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, ExamDeliveryConstants.AUDIO_RESOLUTION_FAILED);
    }
}
