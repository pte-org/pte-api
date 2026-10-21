package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A student or class cannot be added when an already added class or program includes it. */
public class AudienceSourceAlreadyCoveredException extends DomainException {

    public AudienceSourceAlreadyCoveredException() {
        super(HttpStatus.CONFLICT, SessionConstants.AUDIENCE_SOURCE_ALREADY_COVERED, null,
                SessionConstants.AUDIENCE_SOURCE_ALREADY_COVERED_FRIENDLY);
    }
}
