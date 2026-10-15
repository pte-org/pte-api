package com.pte.session.dto.response;

import com.pte.session.internal.dto.response.ProctorOverviewCountsResponse;

import java.time.Instant;
import java.util.List;

public record ProctorWorkspaceOverviewView(
        Instant asOf,
        String timezone,
        Instant dayStart,
        Instant dayEnd,
        ProctorOverviewCountsResponse counts,
        List<ProctorWorkspaceSessionView> prioritySessions
) {
}
