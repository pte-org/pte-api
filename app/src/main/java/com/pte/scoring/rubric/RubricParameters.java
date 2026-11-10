package com.pte.scoring.rubric;

import java.util.List;
import java.util.Map;

/**
 * The editable numbers of one task type's rubric: a 0-10 weight per trait, the Form bands (null when the task has
 * no Form criterion), whether each gate trait is switched on, and the version of the Azure-to-Pearson score mapping.
 * A stored rubric version holds the full set, so a later change to the code defaults never changes it.
 */
public record RubricParameters(Map<String, Integer> traitWeights, List<FormBand> formBands,
                               Map<String, Boolean> gateEnabled, String azureMappingVersion) {

    public RubricParameters {
        traitWeights = Map.copyOf(traitWeights);
        formBands = formBands == null ? null : List.copyOf(formBands);
        gateEnabled = Map.copyOf(gateEnabled);
    }
}
