package com.pte.notification.internal.dto.response;

import com.pte.shared.web.PagedResult;

public record InboxPageResponse(
        PagedResult<InboxItemResponse> page,
        InboxSnapshotResponse snapshot,
        long readRevision) {
}
