package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AudienceSourceNotFoundException extends DomainException {

    public AudienceSourceNotFoundException() {
        super(HttpStatus.NOT_FOUND, SessionConstants.AUDIENCE_SOURCE_NOT_FOUND,
                null, SessionConstants.AUDIENCE_SOURCE_NOT_FOUND_FRIENDLY);
    }
}
