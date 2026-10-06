package com.pte.notification.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InboxNotificationException extends DomainException {
    public InboxNotificationException(HttpStatus status, String code, String userMessage) {
        super(status, code, null, userMessage);
    }
}
