package com.pte.session.internal.dto.response.proctor;

import java.time.Instant;
import java.util.List;

public record ProctorWorkspaceOverviewResponse(
        Instant asOf,
        String timezone,
        Instant dayStart,
        Instant dayEnd,
        ProctorOverviewCountsResponse counts,
        List<ProctorWorkspaceSessionResponse> prioritySessions
) {
}
