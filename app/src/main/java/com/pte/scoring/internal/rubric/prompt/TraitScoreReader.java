package com.pte.scoring.internal.rubric.prompt;

import com.pte.scoring.rubric.TraitDefinition;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the trait scores a model returned. A trait is valid only as a JSON number that is a whole number inside
 * 0..its Pearson maximum (4.0 is the whole number 4); anything else (missing, null, text, a fraction, below 0, above
 * the maximum) is a schema violation. Nothing is ever rounded, clamped or defaulted.
 */
public final class TraitScoreReader {

    private TraitScoreReader() {
    }

    /**
     * @param scores     the valid traits, in rubric order
     * @param violations the keys of the traits that break the schema, in rubric order; empty when all are valid
     */
    public record TraitReading(Map<String, Integer> scores, List<String> violations) {

        public TraitReading {
            scores = Collections.unmodifiableMap(new LinkedHashMap<>(scores));
            violations = List.copyOf(violations);
        }
    }

    public static TraitReading read(JsonNode content, List<TraitDefinition> traits) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        List<String> violations = new ArrayList<>();
        for (TraitDefinition trait : traits) {
            Integer score = wholeNumber(content.get(trait.key()), trait.scale().maxFor(null));
            if (score == null) {
                violations.add(trait.key());
            } else {
                scores.put(trait.key(), score);
            }
        }
        return new TraitReading(scores, violations);
    }

    private static Integer wholeNumber(JsonNode value, int max) {
        if (value == null || !value.isNumber()) {
            return null;
        }
        BigDecimal number = value.decimalValue();
        if (number.signum() != 0 && number.stripTrailingZeros().scale() > 0) {
            return null;
        }
        if (number.compareTo(BigDecimal.ZERO) < 0 || number.compareTo(BigDecimal.valueOf(max)) > 0) {
            return null;
        }
        return number.intValueExact();
    }
}
