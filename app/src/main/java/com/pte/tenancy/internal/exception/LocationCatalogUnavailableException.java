package com.pte.tenancy.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.tenancy.internal.constant.TenancyConstants;
import org.springframework.http.HttpStatus;

/** Raised when the configured location catalog cannot be read or parsed. */
public class LocationCatalogUnavailableException extends DomainException {

    public LocationCatalogUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, TenancyConstants.LOCATION_CATALOG_UNAVAILABLE);
        initCause(cause);
    }
}
