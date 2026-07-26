package com.pte.media.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.media.constant.MediaConstants;
import org.springframework.http.HttpStatus;

public class UnsupportedContentTypeException extends DomainException {

    public UnsupportedContentTypeException() {
        super(HttpStatus.BAD_REQUEST, MediaConstants.UNSUPPORTED_CONTENT_TYPE);
    }
}
