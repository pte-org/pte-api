package com.pte.media.internal.exception;

import com.pte.media.internal.constant.MediaConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class MediaAlreadyUploadedException extends DomainException {

    public MediaAlreadyUploadedException() {
        super(HttpStatus.CONFLICT, MediaConstants.MEDIA_ALREADY_UPLOADED);
    }
}
