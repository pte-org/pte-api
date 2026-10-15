package com.pte.session.internal.dto.response;

import com.pte.shared.web.PageMeta;

import java.time.Instant;
import java.util.List;

public record ProctorAssignedSessionsPageResponse(
        List<AssignedProctorSessionResponse> data,
        PageMeta meta,
        Instant asOf,
        String timezone,
        Instant dayStart,
        Instant dayEnd
) {
}
