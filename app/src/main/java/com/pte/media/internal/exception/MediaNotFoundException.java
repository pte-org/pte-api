package com.pte.media.internal.exception;

import com.pte.media.internal.constant.MediaConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class MediaNotFoundException extends DomainException {

    public MediaNotFoundException() {
        super(HttpStatus.NOT_FOUND, MediaConstants.MEDIA_NOT_FOUND);
    }
}
