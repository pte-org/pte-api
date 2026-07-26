package com.pte.media.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.media.constant.MediaConstants;
import org.springframework.http.HttpStatus;

public class MediaAlreadyUploadedException extends DomainException {

    public MediaAlreadyUploadedException() {
        super(HttpStatus.CONFLICT, MediaConstants.MEDIA_ALREADY_UPLOADED);
    }
}
