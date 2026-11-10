package com.pte.reporting.internal.dto.response;

import com.pte.scoring.dto.response.RubricParametersView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Every step from trait scores to the 10-90 scores of one attempt (FR-07), for its Host or assigned Examiner.
 * A published attempt is read from its immutable snapshot ({@code schemaVersion} 1 or 2; schema 1 has scores only,
 * {@code traceAvailable=false}); an unpublished one is recomputed live from stored trait scores with no AI call
 * ({@code schemaVersion} null), counting unselected AI scores as the Host's review screen does.
 * {@code rubricParameters} are those of the rubric version pinned on the exam template, present only when that
 * version is ACTIVE or RETIRED. {@code overall} is null when the exam did not test all four skills. No transcript,
 * response content or student identity is included.
 */
public record ScoreExplanationResponse(
        UUID attemptPublicId,
        UUID sessionPublicId,
        boolean published,
        Instant publishedAt,
        Integer schemaVersion,
        boolean traceAvailable,
        boolean includesUnselectedAiScores,
        UUID examDefinitionPublicId,
        Integer examDefinitionVersion,
        UUID rubricVersionPublicId,
        RubricParametersView rubricParameters,
        List<ItemExplanationResponse> items,
        List<TaskTypeAverageResponse> taskTypeAverages,
        List<SkillExplanationResponse> skills,
        SkillExplanationResponse overall) {
}
