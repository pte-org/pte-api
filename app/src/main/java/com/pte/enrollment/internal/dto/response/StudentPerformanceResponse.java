package com.pte.enrollment.internal.dto.response;

import java.time.Instant;
import java.util.List;

/** Server-owned official performance summary for one student. */
public record StudentPerformanceResponse(
        long attemptCount,
        Instant latestAttemptAt,
        Integer averageOverall,
        boolean averageOverallAvailable,
        List<StudentSkillPerformanceResponse> skills,
        String calculationScope) {
}
