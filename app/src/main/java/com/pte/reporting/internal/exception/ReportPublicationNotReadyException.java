package com.pte.reporting.internal.exception;

import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ReportPublicationNotReadyException extends DomainException {

    public ReportPublicationNotReadyException(Object blockers) {
        super(HttpStatus.CONFLICT, ReportingConstants.PUBLICATION_NOT_READY, blockers,
                ReportingConstants.PUBLICATION_NOT_READY_MESSAGE);
    }
}
