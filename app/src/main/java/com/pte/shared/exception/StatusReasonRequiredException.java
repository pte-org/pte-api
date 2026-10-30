package com.pte.shared.exception;

import com.pte.shared.constant.SharedConstants;
import org.springframework.http.HttpStatus;

/** A status change that needs an explanation (for example a rejection) arrived without one. */
public class StatusReasonRequiredException extends DomainException {

    /** @param message the caller-facing explanation; the machine code is the shared validation code */
    public StatusReasonRequiredException(String message) {
        super(HttpStatus.BAD_REQUEST, SharedConstants.VALIDATION_FALLBACK, null, message, message);
    }

    /** @return the trimmed reason, or throws when it is blank */
    public static String requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw new StatusReasonRequiredException(message);
        }
        return reason.trim();
    }
}
