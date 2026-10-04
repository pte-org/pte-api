package com.pte.support.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.support.internal.constant.SupportConstants;
import org.springframework.http.HttpStatus;

public class SupportTicketNotFoundException extends DomainException {

    public SupportTicketNotFoundException() {
        super(HttpStatus.NOT_FOUND, SupportConstants.TICKET_NOT_FOUND,
                null, SupportConstants.TICKET_NOT_FOUND_FRIENDLY);
    }
}
