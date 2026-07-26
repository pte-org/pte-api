package com.pte.media.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.media.constant.MediaConstants;
import org.springframework.http.HttpStatus;

public class MediaNotFoundException extends DomainException {

    public MediaNotFoundException() {
        super(HttpStatus.NOT_FOUND, MediaConstants.MEDIA_NOT_FOUND);
    }
}
