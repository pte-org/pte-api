package com.pte.media.internal.exception;

import com.pte.media.internal.constant.MediaConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** MinIO/S3 presigned URL generation failed (storage unreachable/misconfigured). */
public class PresignFailedException extends DomainException {

    public PresignFailedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, MediaConstants.PRESIGN_FAILED);
    }
}
