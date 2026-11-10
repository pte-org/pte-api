package com.pte.scoring.internal.rubric.dto;

import java.util.List;
import java.util.UUID;

/**
 * One task type's rubric as it applies in one rubric version: structure and descriptions from the code catalog,
 * numbers (weights, gates, Form bands, Azure mapping) from the version row. {@code formBands}, {@code objectiveRule}
 * and {@code azureMapping} are null when they do not apply to the task type.
 */
public record TaskTypeRubricResponse(
        String taskType,
        List<String> skills,
        UUID versionPublicId,
        int versionNo,
        int catalogVersion,
        List<TraitDetailsResponse> traits,
        List<GateTraitResponse> gates,
        List<FormBandResponse> formBands,
        ObjectiveRuleResponse objectiveRule,
        AzureMappingResponse azureMapping) {
}
