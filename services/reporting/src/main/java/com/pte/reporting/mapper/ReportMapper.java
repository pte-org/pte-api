package com.pte.reporting.mapper;

import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.dto.response.ReportResponse;
import com.pte.reporting.dto.response.SkillScoreResponse;
import com.pte.reporting.service.AttemptScoreSummary;
import com.pte.reporting.service.SkillScore;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ReportMapper {

    private ReportMapper() {
    }

    public static ReportResponse toResponse(AttemptReport report, AttemptScoreSummary summary) {
        List<SkillScoreResponse> communicative = filterAndMap(summary.skillScores(), Skill::isCommunicative);
        List<SkillScoreResponse> enabling = filterAndMap(summary.skillScores(), skill -> !skill.isCommunicative());
        return new ReportResponse(
                report.getAttemptPublicId(),
                report.getSessionPublicId(),
                report.isPublished(),
                report.getPublishedAt(),
                toResponse("OVERALL", summary.overall()),
                communicative,
                enabling);
    }

    private static List<SkillScoreResponse> filterAndMap(Map<Skill, SkillScore> skillScores, Predicate<Skill> filter) {
        return skillScores.entrySet().stream()
                .filter(e -> filter.test(e.getKey()))
                .map(e -> toResponse(e.getKey().name(), e.getValue()))
                .toList();
    }

    private static SkillScoreResponse toResponse(String name, SkillScore score) {
        return new SkillScoreResponse(name, score.score(), score.sufficientData());
    }
}
