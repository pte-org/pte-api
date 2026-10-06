package com.pte.notification.internal.dto.response;

import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementResponse(
        UUID publicId,
        UUID authorUserPublicId,
        String title,
        String body,
        InboxCategory category,
        InboxImportance importance,
        Instant affectedFrom,
        Instant affectedUntil,
        UUID publishedContentPublicId,
        Instant publishedAt,
        UUID correctionOfPublicId,
        long version,
        boolean published,
        Instant createdAt,
        Instant updatedAt,
        AnnouncementDeliverySummary delivery) {
}
