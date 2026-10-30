package com.pte.chat.internal.exception;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ChatRequestInFlightException extends DomainException {

    public ChatRequestInFlightException() {
        super(HttpStatus.CONFLICT, ChatConstants.ERR_REQUEST_IN_FLIGHT, null, ChatConstants.REQUEST_IN_FLIGHT_MESSAGE);
    }
}
