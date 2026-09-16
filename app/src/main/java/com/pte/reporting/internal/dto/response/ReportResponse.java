package com.pte.reporting.internal.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code overall} is {@code null} when the exam didn't cover all 4 skills
 * (spec FR-20 — "not applicable"), distinct from a present {@link
 * SkillScoreResponse} with {@code sufficientData=false} ("not enough data
 * yet"). {@code communicativeSkills} only ever contains skills that were
 * actually tested (FR-19) — no {@code enablingSkills} field: Grammar/Oral
 * Fluency/Pronunciation/Spelling/Vocabulary/Written Discourse are out of
 * scope, the pinned ScoreTemplate has no weight column for them.
 */
public record ReportResponse(
        UUID attemptPublicId,
        UUID sessionPublicId,
        boolean published,
        Instant publishedAt,
        SkillScoreResponse overall,
        List<SkillScoreResponse> communicativeSkills) {
}
