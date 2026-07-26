package com.pte.reporting.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.reporting.constant.ReportingConstants;
import org.springframework.http.HttpStatus;

/** Also used to hide an unpublished report from a student (404, not 403 — no existence leak). */
public class ReportNotFoundException extends DomainException {

    public ReportNotFoundException() {
        super(HttpStatus.NOT_FOUND, ReportingConstants.REPORT_NOT_FOUND);
    }
}
