package com.pte.authoring.domain.exception;

import com.pte.authoring.constant.AuthoringConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A non-platform caller attempted to write SHARED (platform-bank) content. */
public class SharedWriteForbiddenException extends DomainException {

    public SharedWriteForbiddenException() {
        super(HttpStatus.FORBIDDEN, AuthoringConstants.SHARED_WRITE_FORBIDDEN);
    }
}
