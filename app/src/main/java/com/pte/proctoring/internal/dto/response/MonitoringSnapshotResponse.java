package com.pte.proctoring.internal.dto.response;

import com.pte.session.dto.response.ProctorMonitoringSessionView;
import com.pte.shared.web.PagedResult;

import java.time.Instant;

/**
 * Authoritative polling snapshot returned every 5 seconds for the Proctor room.
 */
public record MonitoringSnapshotResponse(
        ProctorMonitoringSessionView session,
        MonitoringSummaryResponse summary,
        PagedResult<MonitoringStudentResponse> students,
        Instant snapshotAt
) {
}
