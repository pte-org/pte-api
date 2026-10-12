package com.pte.session.dto.response;

public record AttendanceSummaryView(
        long totalEnrollments,
        long unmarkedCount,
        long presentCount,
        long absentCount
) {
}
