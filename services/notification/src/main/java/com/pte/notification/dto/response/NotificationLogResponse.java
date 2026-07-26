package com.pte.notification.dto.response;

import com.pte.notification.domain.enums.NotificationStatus;
import com.pte.notification.domain.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationLogResponse(UUID publicId, String recipientEmail, NotificationType notificationType,
                                       String subject, NotificationStatus status, Instant sentAt) {
}
