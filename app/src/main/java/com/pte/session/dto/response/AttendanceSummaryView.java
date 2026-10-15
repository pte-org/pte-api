package com.pte.session.dto.response;

public record AttendanceSummaryView(
        long totalEnrollments,
        long unmarkedCount,
        long presentCount,
        long absentCount,
        long lateCount
) {
    public AttendanceSummaryView(long totalEnrollments, long unmarkedCount, long presentCount, long absentCount) {
        this(totalEnrollments, unmarkedCount, presentCount, absentCount, 0L);
    }
}
