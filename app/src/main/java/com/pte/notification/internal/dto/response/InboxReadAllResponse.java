package com.pte.notification.internal.dto.response;

public record InboxReadAllResponse(long markedCount, long watermark, long readRevision) {
}
