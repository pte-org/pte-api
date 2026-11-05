package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.rubric.FormBand;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.RubricParameters;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitScale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks a rubric version's parameters before they are stored: size limit, strict shape (codec), a supported Azure
 * band-mapping table, and the same structure as the code catalog (exactly the catalog's task types, trait keys and
 * gate keys; every trait scale equal to the catalog's, per-word marker included; Form bands exactly where the catalog
 * has a Form rule). Weights are integers 0-10, every catalog gate weighs
 * more than 0, each trait-scored task's weights sum above 0, and Form bands are well-formed, do not overlap, stay
 * within the Form scale and include its maximum band.
 *
 * <p>catalog_version rule: a version declaring a catalog_version other than the code's is accepted only because its
 * structure still matches the code catalog (descriptions are always shown from code); any structural difference is
 * rejected here whatever catalog_version it declares.
 */
@Component
public class RubricParametersValidator {

    private final RubricCatalog catalog;
    private final RubricParametersCodec codec;

    public RubricParametersValidator(RubricCatalog catalog, RubricParametersCodec codec) {
        this.catalog = catalog;
        this.codec = codec;
    }

    public StoredRubricParameters validate(String parametersJson, int catalogVersion) {
        if (parametersJson != null
                && parametersJson.getBytes(StandardCharsets.UTF_8).length > RubricConstants.MAX_PARAMETERS_JSON_BYTES) {
            throw invalid(RubricConstants.RUBRIC_PARAMETERS_TOO_LARGE);
        }
        if (catalogVersion < 1) {
            throw invalid(RubricConstants.RUBRIC_CATALOG_VERSION_INVALID);
        }
        StoredRubricParameters stored = codec.decode(parametersJson);
        if (!RubricConstants.SUPPORTED_AZURE_BAND_MAPPING_VERSIONS.contains(stored.azureBandMappingVersion())) {
            throw invalid(RubricConstants.RUBRIC_AZURE_MAPPING_VERSION_INVALID);
        }
        for (String taskType : stored.taskTypes().keySet()) {
            if (catalog.find(taskType).isEmpty()) {
                throw invalid(RubricConstants.RUBRIC_TASK_TYPE_UNKNOWN);
            }
        }
        for (TaskRubric task : catalog.all()) {
            RubricParameters parameters = stored.taskTypes().get(task.taskType());
            if (parameters == null) {
                throw invalid(RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
            }
            validateTask(task, parameters, stored.traitScales().get(task.taskType()));
        }
        return stored;
    }

    private static void validateTask(TaskRubric task, RubricParameters parameters, Map<String, TraitScale> scales) {
        Set<String> traitKeys = task.traits().stream().map(TraitDefinition::key).collect(Collectors.toSet());
        requireKnownAndComplete(parameters.traitWeights().keySet(), traitKeys, RubricConstants.RUBRIC_TRAIT_UNKNOWN);
        requireKnownAndComplete(scales.keySet(), traitKeys, RubricConstants.RUBRIC_TRAIT_UNKNOWN);
        if (!scales.equals(StoredRubricParameters.scalesOf(task))) {
            throw invalid(RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
        }
        requireKnownAndComplete(parameters.gateEnabled().keySet(), task.gateTraits(),
                RubricConstants.RUBRIC_GATE_UNKNOWN);
        validateWeights(task, parameters);
        if ((task.formRule() == null) != (parameters.formBands() == null)) {
            throw invalid(RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
        }
        if (parameters.formBands() != null) {
            validateFormBands(parameters.formBands(), formScaleMax(task));
        }
    }

    private static void requireKnownAndComplete(Set<String> given, Set<String> expected, String unknownCode) {
        if (!expected.containsAll(given)) {
            throw invalid(unknownCode);
        }
        if (!given.containsAll(expected)) {
            throw invalid(RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
        }
    }

    private static void validateWeights(TaskRubric task, RubricParameters parameters) {
        int sum = 0;
        for (Map.Entry<String, Integer> entry : parameters.traitWeights().entrySet()) {
            int weight = entry.getValue();
            if (weight < RubricConstants.MIN_TRAIT_WEIGHT || weight > RubricConstants.MAX_TRAIT_WEIGHT) {
                throw invalid(RubricConstants.RUBRIC_WEIGHT_INVALID);
            }
            if (weight == 0 && task.gateTraits().contains(entry.getKey())) {
                throw invalid(RubricConstants.RUBRIC_GATE_WEIGHT_ZERO);
            }
            sum += weight;
        }
        if (!task.isObjective() && sum == 0) {
            throw invalid(RubricConstants.RUBRIC_WEIGHT_SUM_ZERO);
        }
    }

    private static int formScaleMax(TaskRubric task) {
        return task.trait(RubricTraits.FORM)
                .map(form -> form.scale().maxFor(null))
                .orElseThrow(() -> invalid(RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH));
    }

    /** Non-empty, each band 0..scale max with minWords 0..maxWords, no overlap, and one band at the scale maximum. */
    private static void validateFormBands(List<FormBand> bands, int scaleMax) {
        if (bands.isEmpty() || bands.stream().noneMatch(band -> band.band() == scaleMax)) {
            throw invalid(RubricConstants.RUBRIC_FORM_BANDS_INVALID);
        }
        for (FormBand band : bands) {
            if (band.band() < 0 || band.band() > scaleMax || band.minWords() < 0
                    || band.minWords() > band.maxWords()) {
                throw invalid(RubricConstants.RUBRIC_FORM_BANDS_INVALID);
            }
        }
        List<FormBand> sorted = bands.stream().sorted(Comparator.comparingInt(FormBand::minWords)).toList();
        for (int index = 1; index < sorted.size(); index++) {
            if (sorted.get(index).minWords() <= sorted.get(index - 1).maxWords()) {
                throw invalid(RubricConstants.RUBRIC_FORM_BANDS_INVALID);
            }
        }
    }

    private static RubricVersionException invalid(String code) {
        return new RubricVersionException(HttpStatus.BAD_REQUEST, code);
    }
}
