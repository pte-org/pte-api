package com.pte.media.internal.exception;

import com.pte.media.internal.constant.MediaConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A presigned GET was requested before {@code completeUpload} marked the object UPLOADED. */
public class MediaNotYetUploadedException extends DomainException {

    public MediaNotYetUploadedException() {
        super(HttpStatus.CONFLICT, MediaConstants.MEDIA_NOT_YET_UPLOADED);
    }
}
