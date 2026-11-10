package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.rubric.RubricParameters;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitScale;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The decoded {@code scoring_rubric_versions.parameters} of one version: the Azure band-mapping table it uses, the
 * full parameters of every task type (each carrying that mapping version as its {@code azureMappingVersion}), and
 * the trait scales the version was authored against (task type -> trait -> scale).
 */
public record StoredRubricParameters(int azureBandMappingVersion, Map<String, RubricParameters> taskTypes,
                                     Map<String, Map<String, TraitScale>> traitScales) {

    public StoredRubricParameters {
        taskTypes = Collections.unmodifiableMap(new LinkedHashMap<>(taskTypes));
        Map<String, Map<String, TraitScale>> copied = new LinkedHashMap<>();
        traitScales.forEach((taskType, scales) ->
                copied.put(taskType, Collections.unmodifiableMap(new LinkedHashMap<>(scales))));
        traitScales = Collections.unmodifiableMap(copied);
    }

    /** The scales a task's traits have in the code catalog, in trait order: what a stored version must match. */
    public static Map<String, TraitScale> scalesOf(TaskRubric task) {
        Map<String, TraitScale> scales = new LinkedHashMap<>();
        for (TraitDefinition trait : task.traits()) {
            scales.put(trait.key(), trait.scale());
        }
        return scales;
    }
}
