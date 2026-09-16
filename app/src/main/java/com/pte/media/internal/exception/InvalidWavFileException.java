package com.pte.media.internal.exception;

import com.pte.media.domain.MediaObject;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * An audio-prompt upload's bytes can't be parsed as valid WAV at complete-upload
 * time (corrupt, wrong format, truncated) — a data-completeness problem, not a
 * call/storage failure, so 422 rather than {@link PresignFailedException}'s 503.
 * Thrown before {@link MediaObject#markUploaded()} is ever called for this
 * object — it must never end up marked usable without a known duration.
 */
public class InvalidWavFileException extends DomainException {

    public InvalidWavFileException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, MediaConstants.INVALID_WAV_FILE);
    }
}
