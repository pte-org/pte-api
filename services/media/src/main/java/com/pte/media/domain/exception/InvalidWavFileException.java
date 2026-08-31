package com.pte.media.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.media.constant.MediaConstants;
import org.springframework.http.HttpStatus;

/**
 * An audio-prompt upload's bytes can't be parsed as valid WAV at complete-upload
 * time (corrupt, wrong format, truncated) — a data-completeness problem, not a
 * call/storage failure, so 422 rather than {@link PresignFailedException}'s 503.
 * Thrown before {@link com.pte.media.domain.MediaObject#markUploaded()} is ever
 * called for this object — it must never end up marked usable without a known
 * duration (plans/phat-speaking-dynamic-prep-timing).
 */
public class InvalidWavFileException extends DomainException {

    public InvalidWavFileException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, MediaConstants.INVALID_WAV_FILE);
    }
}
