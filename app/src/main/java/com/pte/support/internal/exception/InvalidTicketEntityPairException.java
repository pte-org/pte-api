package com.pte.support.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.support.internal.constant.SupportConstants;
import org.springframework.http.HttpStatus;

public class InvalidTicketEntityPairException extends DomainException {

    public InvalidTicketEntityPairException() {
        super(HttpStatus.BAD_REQUEST, SupportConstants.INVALID_TICKET_ENTITY_PAIR,
                null, SupportConstants.INVALID_TICKET_ENTITY_PAIR_FRIENDLY);
    }
}
