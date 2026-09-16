package com.pte.media.internal.exception;

import com.pte.media.internal.constant.MediaConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class UnsupportedContentTypeException extends DomainException {

    public UnsupportedContentTypeException() {
        super(HttpStatus.BAD_REQUEST, MediaConstants.UNSUPPORTED_CONTENT_TYPE);
    }
}
