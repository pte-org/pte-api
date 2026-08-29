package com.pte.media.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.media.constant.MediaConstants;
import org.springframework.http.HttpStatus;

/** A presigned GET was requested before {@code completeUpload} marked the object UPLOADED. */
public class MediaNotYetUploadedException extends DomainException {

    public MediaNotYetUploadedException() {
        super(HttpStatus.CONFLICT, MediaConstants.MEDIA_NOT_YET_UPLOADED);
    }
}
