package com.pte.notification.internal.dto.response;

public record AnnouncementDeliverySummary(
        long audienceCount,
        long pendingCount,
        long deliveredCount,
        long failedCount,
        long suppressedCount,
        long readCount) {
}
