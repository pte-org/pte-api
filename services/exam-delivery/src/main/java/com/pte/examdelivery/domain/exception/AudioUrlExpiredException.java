package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** The pinned {@code audioUrlExpiresAt} has passed — should not happen under normal operation (see Phase 6 Risks). */
public class AudioUrlExpiredException extends DomainException {

    public AudioUrlExpiredException() {
        super(HttpStatus.GONE, ExamDeliveryConstants.AUDIO_URL_EXPIRED);
    }
}
