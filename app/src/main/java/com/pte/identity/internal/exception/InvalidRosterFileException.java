package com.pte.identity.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Raised when an uploaded roster is missing, too large, or not a valid XLSX workbook. */
public class InvalidRosterFileException extends DomainException {

    public InvalidRosterFileException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
