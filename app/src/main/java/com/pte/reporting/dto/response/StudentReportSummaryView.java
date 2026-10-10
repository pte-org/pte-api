package com.pte.reporting.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only report visibility and immutable score snapshot for a student workspace. */
public record StudentReportSummaryView(
        UUID attemptPublicId,
        UUID sessionPublicId,
        boolean published,
        Instant publishedAt,
        boolean immutableSnapshot,
        ReportSkillScoreView overall,
        List<ReportSkillScoreView> skills) {
}
