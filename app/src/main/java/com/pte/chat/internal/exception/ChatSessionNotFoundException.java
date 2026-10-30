package com.pte.chat.internal.exception;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ChatSessionNotFoundException extends DomainException {

    public ChatSessionNotFoundException() {
        super(HttpStatus.NOT_FOUND, ChatConstants.ERR_SESSION_NOT_FOUND, null, ChatConstants.SESSION_NOT_FOUND_MESSAGE);
    }
}
