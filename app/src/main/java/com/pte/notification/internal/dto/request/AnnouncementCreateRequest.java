package com.pte.notification.internal.dto.request;

import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementCreateRequest(
        String title,
        String body,
        InboxCategory category,
        InboxImportance importance,
        Instant affectedFrom,
        Instant affectedUntil,
        UUID correctionOfPublicId) {
}
