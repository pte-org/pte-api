package com.pte.proctor.domain.exception;

import com.pte.proctor.constant.ProctorConstants;
import org.springframework.messaging.MessagingException;

public class StompSubscriptionForbiddenException extends MessagingException {

    public StompSubscriptionForbiddenException() {
        super(ProctorConstants.STOMP_SUBSCRIPTION_FORBIDDEN);
    }
}
