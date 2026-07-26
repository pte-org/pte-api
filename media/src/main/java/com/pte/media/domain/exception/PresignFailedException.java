package com.pte.media.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.media.constant.MediaConstants;
import org.springframework.http.HttpStatus;

/** MinIO/S3 presigned URL generation failed (storage unreachable/misconfigured). */
public class PresignFailedException extends DomainException {

    public PresignFailedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, MediaConstants.PRESIGN_FAILED);
    }
}
