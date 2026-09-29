package com.pte.proctoring.internal.dto.response;

import java.util.List;

public record SecurityAuditPageResponse(List<SecurityAuditEntryResponse> entries, String nextCursor) {

    public SecurityAuditPageResponse {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
