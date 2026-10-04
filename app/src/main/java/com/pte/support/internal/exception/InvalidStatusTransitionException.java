package com.pte.support.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.internal.constant.SupportConstants;
import org.springframework.http.HttpStatus;

public class InvalidStatusTransitionException extends DomainException {

    public InvalidStatusTransitionException(TicketStatus from, TicketStatus to) {
        super(HttpStatus.BAD_REQUEST, SupportConstants.INVALID_STATUS_TRANSITION,
                null, SupportConstants.INVALID_STATUS_TRANSITION_FRIENDLY,
                "Cannot transition ticket from " + from + " to " + to);
    }
}
