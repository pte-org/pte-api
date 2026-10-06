package com.pte.notification.internal.dto.response;

public record InboxUnreadCountResponse(long unreadCount, long readRevision) {
}
