package com.pte.overview.internal.dto.response;

import com.pte.session.UpcomingSessionView;

import java.util.List;
import java.util.Map;

/** Payload records of the host dashboard sections that exist in the core release. */
public final class HostSectionData {

    private HostSectionData() {
    }

    /**
     * {@code total}/{@code active}/{@code suspended} are current student counts;
     * {@code activeLearners} counts distinct students who started an attempt in the window.
     */
    public record Learners(long total, long active, long suspended, TrendValue newInWindow,
            TrendValue activeLearners, long unassigned) {
    }

    public record Quota(long used, long limit, long remaining) {
    }

    public record Academics(long organizations, long programs, long activeClasses) {
    }

    /** Active, non-deleted exam staff. */
    public record Staff(long proctors, long examiners) {
    }

    /** {@code byStatus} holds every session status (0 when none); {@code upcoming} is a short list. */
    public record Exams(Map<String, Long> byStatus, TrendValue created, List<UpcomingSessionView> upcoming) {
    }

    /** {@code monthlySubmitted} is oldest first and ends with the current (partial) month. */
    public record Attempts(TrendValue submitted, long inProgressNow, List<MonthlyPoint> monthlySubmitted) {
    }
}
