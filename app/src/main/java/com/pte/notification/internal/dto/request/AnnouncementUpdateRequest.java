package com.pte.notification.internal.dto.request;

import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;

import java.time.Instant;

public record AnnouncementUpdateRequest(
        String title,
        String body,
        InboxCategory category,
        InboxImportance importance,
        Instant affectedFrom,
        Instant affectedUntil,
        Long expectedDraftVersion) {
}
