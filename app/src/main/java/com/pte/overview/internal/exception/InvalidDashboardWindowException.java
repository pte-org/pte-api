package com.pte.overview.internal.exception;

import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Rejects a {@code windowDays} value outside the supported 7/30/90 set. */
public class InvalidDashboardWindowException extends DomainException {

    public InvalidDashboardWindowException() {
        super(HttpStatus.BAD_REQUEST, OverviewConstants.INVALID_DASHBOARD_WINDOW);
    }
}
