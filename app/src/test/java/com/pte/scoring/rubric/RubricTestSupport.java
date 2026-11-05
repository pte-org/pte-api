package com.pte.scoring.rubric;

import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Shared builders for the rubric tests: catalog access, effective rubrics, trait-score sets, error assertions. */
final class RubricTestSupport {

    static final RubricCatalog CATALOG = new RubricCatalogImpl();

    private RubricTestSupport() {
    }

    static TaskRubric task(String taskType) {
        return CATALOG.find(taskType).orElseThrow();
    }

    static EffectiveRubric effective(String taskType) {
        return new EffectiveRubric(task(taskType), CATALOG.defaultParameters(taskType));
    }

    /** Default parameters with the given weights replaced. */
    static EffectiveRubric withWeights(String taskType, Map<String, Integer> overrides) {
        RubricParameters defaults = CATALOG.defaultParameters(taskType);
        Map<String, Integer> weights = new HashMap<>(defaults.traitWeights());
        weights.putAll(overrides);
        return new EffectiveRubric(task(taskType),
                new RubricParameters(weights, defaults.formBands(), defaults.gateEnabled(),
                        defaults.azureMappingVersion()));
    }

    /** Default parameters without the weight of one trait. */
    static EffectiveRubric withoutWeight(String taskType, String traitKey) {
        RubricParameters defaults = CATALOG.defaultParameters(taskType);
        Map<String, Integer> weights = new HashMap<>(defaults.traitWeights());
        weights.remove(traitKey);
        return new EffectiveRubric(task(taskType),
                new RubricParameters(weights, defaults.formBands(), defaults.gateEnabled(),
                        defaults.azureMappingVersion()));
    }

    /** Default parameters with every gate trait of the task switched on or off. */
    static EffectiveRubric withGates(String taskType, boolean enabled) {
        RubricParameters defaults = CATALOG.defaultParameters(taskType);
        Map<String, Boolean> gates = new HashMap<>();
        task(taskType).gateTraits().forEach(key -> gates.put(key, enabled));
        return new EffectiveRubric(task(taskType),
                new RubricParameters(defaults.traitWeights(), defaults.formBands(), gates,
                        defaults.azureMappingVersion()));
    }

    static TextMetrics metrics(int words) {
        return new TextMetrics(words, 3, false, true, false);
    }

    /** Alternating key, score pairs in the given order. */
    static Map<String, Integer> scores(Object... keyAndScore) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < keyAndScore.length; i += 2) {
            result.put((String) keyAndScore[i], (Integer) keyAndScore[i + 1]);
        }
        return result;
    }

    static TraitScoreSet set(Map<String, Integer> scores) {
        return new TraitScoreSet(scores, null);
    }

    static void assertRubricError(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(RubricScoreException.class)
                .extracting(ex -> ((RubricScoreException) ex).getCode()).isEqualTo(code);
    }
}
