package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Pinned policy requires a device check but {@code StartAttemptRequest.deviceCheckConfirmed} was not true. */
public class DeviceCheckRequiredException extends DomainException {

    public DeviceCheckRequiredException() {
        super(HttpStatus.CONFLICT, AttemptConstants.DEVICE_CHECK_REQUIRED);
    }
}
