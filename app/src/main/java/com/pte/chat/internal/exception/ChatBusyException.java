package com.pte.chat.internal.exception;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ChatBusyException extends DomainException {

    public ChatBusyException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, ChatConstants.ERR_BUSY, null,
                "The assistant is busy right now. Your message was not counted. Please try again in a moment.");
    }
}
