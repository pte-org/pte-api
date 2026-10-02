package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionNotClosedForReportPublicationException extends DomainException {

    public SessionNotClosedForReportPublicationException() {
        super(HttpStatus.CONFLICT, SessionConstants.REPORT_PUBLICATION_REQUIRES_CLOSED_SESSION,
                null, SessionConstants.REPORT_PUBLICATION_REQUIRES_CLOSED_SESSION_MESSAGE);
    }
}
