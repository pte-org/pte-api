package com.pte.notification.internal.dto.response;

import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxTargetType;

import java.time.Instant;
import java.util.UUID;

public record InboxItemResponse(
        UUID publicId,
        InboxNotificationType notificationType,
        InboxCategory category,
        InboxImportance importance,
        String title,
        String body,
        InboxTargetType targetType,
        UUID targetPublicId,
        long sequenceNo,
        Instant deliveredAt,
        Instant readAt) {

    public boolean unread() {
        return readAt == null;
    }
}
