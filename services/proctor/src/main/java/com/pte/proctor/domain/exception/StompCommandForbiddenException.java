package com.pte.proctor.domain.exception;

import com.pte.proctor.constant.ProctorConstants;
import org.springframework.messaging.MessagingException;

public class StompCommandForbiddenException extends MessagingException {

    public StompCommandForbiddenException() {
        super(ProctorConstants.STOMP_COMMAND_FORBIDDEN);
    }
}
