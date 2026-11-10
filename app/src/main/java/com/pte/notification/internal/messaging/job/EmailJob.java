package com.pte.notification.internal.messaging.job;

import java.util.UUID;

/**
 * One mail to send. {@code htmlBody} is optional: when present the mail goes out as HTML with {@code body} as its
 * plain-text twin. Jobs queued before the field existed arrive without it and are sent as plain text.
 */
public record EmailJob(UUID notificationLogPublicId, String recipientEmail, String subject, String body,
        String htmlBody) {

    public EmailJob(UUID notificationLogPublicId, String recipientEmail, String subject, String body) {
        this(notificationLogPublicId, recipientEmail, subject, body, null);
    }
}
