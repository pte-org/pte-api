package com.pte.reporting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public immutable report snapshot for cross-module student workspace reads. */
public record StudentReportSummaryView(
        UUID attemptPublicId,
        UUID sessionPublicId,
        boolean published,
        Instant publishedAt,
        boolean immutableSnapshot,
        ReportSkillScoreView overall,
        List<ReportSkillScoreView> skills) {
}
