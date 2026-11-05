package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.catalog.AzureBandMappingCatalog;
import com.pte.scoring.internal.rubric.dto.AzureBandThresholdResponse;
import com.pte.scoring.internal.rubric.dto.AzureMappingResponse;
import com.pte.scoring.internal.rubric.dto.FormBandResponse;
import com.pte.scoring.internal.rubric.dto.GateTraitResponse;
import com.pte.scoring.internal.rubric.dto.LevelResponse;
import com.pte.scoring.internal.rubric.dto.ObjectiveRuleResponse;
import com.pte.scoring.internal.rubric.dto.RubricVersionResponse;
import com.pte.scoring.internal.rubric.dto.TaskTypeRubricResponse;
import com.pte.scoring.internal.rubric.dto.TraitDetailsResponse;
import com.pte.scoring.internal.rubric.speech.AzureBandMapping;
import com.pte.scoring.internal.rubric.speech.AzureBandThreshold;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.FormRule;
import com.pte.scoring.rubric.ObjectiveRule;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitSource;

import java.util.List;
import java.util.Optional;

/** Maps an effective rubric and its version row to the read API's records; no business decisions here. */
final class RubricResponseMapper {

    private RubricResponseMapper() {
    }

    static TaskTypeRubricResponse toTaskTypeResponse(EffectiveRubric rubric, ScoringRubricVersion version,
                                                   java.util.Map<String, Integer> referenceWeights) {
        TaskRubric task = rubric.task();
        return new TaskTypeRubricResponse(task.taskType(), task.skills().stream().sorted().toList(),
                version.getPublicId(), version.getVersionNo(), version.getCatalogVersion(),
                task.traits().stream().map(trait -> toTrait(trait, rubric, referenceWeights.get(trait.key()))).toList(),
                task.traits().stream().filter(trait -> task.gateTraits().contains(trait.key()))
                        .map(trait -> new GateTraitResponse(trait.key(), rubric.isGateEnabled(trait.key())))
                        .toList(),
                toFormBands(rubric.effectiveFormRule()), toObjectiveRule(task.objectiveRule()),
                toAzureMapping(rubric));
    }

    static RubricVersionResponse toVersionResponse(ScoringRubricVersion version) {
        return new RubricVersionResponse(version.getPublicId(), version.getVersionNo(), version.getStatus().name(),
                version.getCatalogVersion(), version.getAuthorUserPublicId(), version.getApproverUserPublicId(),
                version.getRejectionReason(), version.getSubmittedAt(), version.getDecidedAt(),
                version.getActivatedAt(), version.getRetiredAt(), version.getLockVersion());
    }

    private static TraitDetailsResponse toTrait(TraitDefinition trait, EffectiveRubric rubric, int defaultWeight) {
        return new TraitDetailsResponse(trait.key(), trait.displayNameVi(), trait.displayNameEn(),
                trait.scale().fixedMax(), trait.scale().isPerWord(), trait.source().name(),
                rubric.weightOf(trait.key()),
                trait.levels().stream().map(level -> new LevelResponse(level.level(), level.text())).toList(), defaultWeight);
    }

    private static List<FormBandResponse> toFormBands(FormRule formRule) {
        if (formRule == null) {
            return null;
        }
        return formRule.bands().stream()
                .map(band -> new FormBandResponse(band.minWords(), band.maxWords(), band.band())).toList();
    }

    private static ObjectiveRuleResponse toObjectiveRule(ObjectiveRule rule) {
        return rule == null ? null : new ObjectiveRuleResponse(rule.code(), rule.descriptionVi());
    }

    /**
     * Only task types with an Azure-scored trait use the mapping table. Its calibration flag comes from the declared
     * table; a version naming a table the code no longer declares is shown as uncalibrated, without thresholds.
     */
    static AzureMappingResponse toAzureMapping(EffectiveRubric rubric) {
        boolean usesAzure = rubric.task().traits().stream().anyMatch(trait -> trait.source() == TraitSource.AZURE);
        if (!usesAzure) {
            return null;
        }
        String version = rubric.parameters().azureMappingVersion();
        Optional<AzureBandMapping> mapping = AzureBandMappingCatalog.find(version);
        boolean calibrated = mapping.map(AzureBandMapping::calibrated).orElse(false);
        return new AzureMappingResponse(version, calibrated,
                calibrated ? null : RubricConstants.AZURE_MAPPING_UNCALIBRATED_LABEL,
                RubricConstants.AZURE_PRONUNCIATION_INPUT_VI, RubricConstants.AZURE_ORAL_FLUENCY_INPUT_VI,
                mapping.map(table -> toThresholds(table.pronunciation())).orElse(List.of()),
                mapping.map(table -> toThresholds(table.oralFluency())).orElse(List.of()));
    }

    private static List<AzureBandThresholdResponse> toThresholds(List<AzureBandThreshold> thresholds) {
        return thresholds.stream()
                .map(threshold -> new AzureBandThresholdResponse(threshold.band(), threshold.minAzureScore()))
                .toList();
    }
}
