package com.pte.session.dto.response;

import com.pte.shared.web.PageMeta;

import java.time.Instant;
import java.util.List;

public record ProctorAssignedSessionsPageView(
        List<ProctorWorkspaceSessionView> data,
        PageMeta meta,
        Instant asOf,
        String timezone,
        Instant dayStart,
        Instant dayEnd
) {
}
