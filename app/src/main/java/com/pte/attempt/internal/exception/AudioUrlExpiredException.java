package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The pinned {@code audioUrlExpiresAt} has passed — should not happen under normal operation. */
public class AudioUrlExpiredException extends DomainException {

    public AudioUrlExpiredException() {
        super(HttpStatus.GONE, AttemptConstants.AUDIO_URL_EXPIRED);
    }
}
