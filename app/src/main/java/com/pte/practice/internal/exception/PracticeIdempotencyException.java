package com.pte.practice.internal.exception;

import com.pte.practice.internal.constant.PracticeConstants;
import org.springframework.http.HttpStatus;

/** Invalid or conflicting retry key for a practice mutation. */
public class PracticeIdempotencyException extends PracticeSessionException {

    public PracticeIdempotencyException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static PracticeIdempotencyException reused() {
        return new PracticeIdempotencyException(HttpStatus.CONFLICT,
                PracticeConstants.PRACTICE_IDEMPOTENCY_KEY_REUSED,
                PracticeConstants.PRACTICE_IDEMPOTENCY_KEY_REUSED_MESSAGE);
    }
}
