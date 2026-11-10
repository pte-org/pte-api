package com.pte.scoring.support;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.RubricParameters;

/**
 * Effective rubrics for speaking scorer tests: the catalog defaults (equal to the seeded first rubric version) with
 * the Azure band-mapping version the seed stores, since the code defaults leave that number to the stored row.
 */
public final class SpeakingRubrics {

    public static final RubricCatalog CATALOG = new RubricCatalogImpl();
    public static final String MAPPING_VERSION = String.valueOf(RubricConstants.AZURE_BAND_MAPPING_VERSION_1);

    private SpeakingRubrics() {
    }

    public static EffectiveRubric of(String taskType) {
        return withMapping(taskType, MAPPING_VERSION);
    }

    public static EffectiveRubric withMapping(String taskType, String mappingVersion) {
        RubricParameters defaults = CATALOG.defaultParameters(taskType);
        return new EffectiveRubric(CATALOG.find(taskType).orElseThrow(), new RubricParameters(
                defaults.traitWeights(), defaults.formBands(), defaults.gateEnabled(), mappingVersion));
    }
}
