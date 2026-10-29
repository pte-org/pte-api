package com.pte.chat.internal.exception;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ChatInvalidRequestException extends DomainException {

    public ChatInvalidRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, ChatConstants.ERR_INVALID_REQUEST, null,
                "Your message could not be sent: " + detail + ".");
    }
}
