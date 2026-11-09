package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.response.RosterRowError;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.List;

/** At least one roster row breaks a format rule; nothing is written and {@code data} lists every row error. */
public class RosterValidationException extends DomainException {
    public RosterValidationException(List<RosterRowError> errors) {
        super(HttpStatus.BAD_REQUEST, IdentityConstants.ROSTER_VALIDATION_FAILED, List.copyOf(errors));
    }
}
