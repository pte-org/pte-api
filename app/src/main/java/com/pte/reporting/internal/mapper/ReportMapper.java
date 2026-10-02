package com.pte.reporting.internal.mapper;

import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.internal.dto.response.ReportResponse;
import com.pte.reporting.internal.dto.response.SkillScoreResponse;
import com.pte.reporting.internal.service.AttemptScoreSummary;
import com.pte.reporting.internal.service.SkillScore;

import java.util.List;
import java.util.Map;

public final class ReportMapper {

    private ReportMapper() {
    }

    public static ReportResponse toResponse(AttemptReport report, AttemptScoreSummary summary) {
        List<SkillScoreResponse> communicative = toSkillList(summary.skillScores());
        SkillScoreResponse overall = summary.overall() == null ? null : toResponse("OVERALL", summary.overall());
        return new ReportResponse(
                report.getAttemptPublicId(),
                report.getSessionPublicId(),
                report.isPublished(),
                report.getPublishedAt(),
                report.getReportSnapshotJson() != null,
                overall,
                communicative);
    }

    private static List<SkillScoreResponse> toSkillList(Map<Skill, SkillScore> skillScores) {
        return skillScores.entrySet().stream()
                .map(e -> toResponse(e.getKey().name(), e.getValue()))
                .toList();
    }

    private static SkillScoreResponse toResponse(String name, SkillScore score) {
        return new SkillScoreResponse(name, score.score(), score.sufficientData());
    }
}
