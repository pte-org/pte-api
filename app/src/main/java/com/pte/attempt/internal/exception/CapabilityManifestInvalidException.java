package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class CapabilityManifestInvalidException extends DomainException {

    public CapabilityManifestInvalidException() {
        super(HttpStatus.BAD_REQUEST, AttemptConstants.CAPABILITY_MANIFEST_INVALID, null,
                AttemptConstants.CAPABILITY_MANIFEST_INVALID_MESSAGE,
                AttemptConstants.CAPABILITY_MANIFEST_INVALID);
    }
}
