package com.pte.reporting.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportResponse(
        UUID attemptPublicId,
        UUID sessionPublicId,
        boolean published,
        Instant publishedAt,
        SkillScoreResponse overall,
        List<SkillScoreResponse> communicativeSkills,
        List<SkillScoreResponse> enablingSkills) {
}
