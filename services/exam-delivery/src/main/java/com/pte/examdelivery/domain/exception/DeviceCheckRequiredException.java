package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** Pinned policy requires a device check but {@code StartAttemptRequest.deviceCheckConfirmed} was not true. */
public class DeviceCheckRequiredException extends DomainException {

    public DeviceCheckRequiredException() {
        super(HttpStatus.CONFLICT, ExamDeliveryConstants.DEVICE_CHECK_REQUIRED);
    }
}
