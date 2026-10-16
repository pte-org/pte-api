package com.pte.tenancy.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.tenancy.internal.constant.TenancyConstants;
import org.springframework.http.HttpStatus;

/** Raised when a ward does not belong to the selected province. */
public class InvalidLocationSelectionException extends DomainException {

    public InvalidLocationSelectionException() {
        super(HttpStatus.BAD_REQUEST, TenancyConstants.LOCATION_SELECTION_INVALID);
    }
}
