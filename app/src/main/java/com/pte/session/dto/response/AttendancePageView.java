package com.pte.session.dto.response;

import com.pte.shared.web.PageMeta;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttendancePageView(
        UUID sessionPublicId,
        List<AttendanceRowView> data,
        PageMeta meta,
        AttendanceSummaryView summary,
        boolean canEditSession,
        String sessionBlockingReason,
        Instant asOf,
        Instant opensAt,
        Instant closesAt,
        Instant attendanceWindowOpensAt
) {
}
