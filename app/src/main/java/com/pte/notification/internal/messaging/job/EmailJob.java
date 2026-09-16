package com.pte.notification.internal.messaging.job;

import java.util.UUID;

public record EmailJob(UUID notificationLogPublicId, String recipientEmail, String subject, String body) {
}
