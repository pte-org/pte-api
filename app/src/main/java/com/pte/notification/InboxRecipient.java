package com.pte.notification;

import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.exception.InboxNotificationException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

public record InboxRecipient(UUID userPublicId, UUID tenantId) {
    public InboxRecipient {
        if (userPublicId == null) {
            throw new InboxNotificationException(HttpStatus.BAD_REQUEST, InboxConstants.INVALID_REQUEST,
                    InboxConstants.INVALID_REQUEST_MESSAGE);
        }
    }
}
