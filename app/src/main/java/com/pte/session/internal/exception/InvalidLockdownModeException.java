package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

    /** A lockdown policy is incompatible with its session exam mode or is missing from non-legacy state. */
public class InvalidLockdownModeException extends DomainException {

    public InvalidLockdownModeException(String reason) {
        this(SessionConstants.LOCKDOWN_MODE_INVALID_FOR_EXAM_MODE,
                SessionConstants.LOCKDOWN_MODE_INVALID_FOR_EXAM_MODE_FRIENDLY, reason);
    }

    public static InvalidLockdownModeException required() {
        return new InvalidLockdownModeException(SessionConstants.LOCKDOWN_MODE_REQUIRED,
                SessionConstants.LOCKDOWN_MODE_REQUIRED_FRIENDLY,
                SessionConstants.LOCKDOWN_MODE_REQUIRED_FRIENDLY);
    }

    private InvalidLockdownModeException(String code, String userMessage, String reason) {
        super(HttpStatus.BAD_REQUEST, code, null, userMessage, reason);
    }
}
