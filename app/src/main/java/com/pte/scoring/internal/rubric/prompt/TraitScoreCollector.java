package com.pte.scoring.internal.rubric.prompt;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.rubric.TraitDefinition;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fails closed with a bounded retry: when the model's answer breaks the trait schema, the very same call (same
 * prompt, same schema) is made again, at most {@link #MAX_EXTRA_CALLS} more times; the values are never repaired.
 * Each call goes through the vendor client, so all of them share the running job's {@code AiJobBudget}. When every
 * call breaks the schema the answer fails with {@code PROVIDER_RESPONSE_INVALID}. Provider failures are not retried
 * here (the client's guard owns HTTP retries).
 */
public final class TraitScoreCollector {

    public static final int MAX_EXTRA_CALLS = 2;
    public static final String ATTEMPT_COUNT = "count";
    public static final String ATTEMPT_VIOLATIONS = "violations";

    private static final String ATTEMPT = "attempt";
    private static final String FIELDS = "fields";

    private TraitScoreCollector() {
    }

    /**
     * @param response the model answer whose traits were valid
     * @param scores   the traits, in rubric order
     * @param attempts how many calls it took and which fields each rejected call broke (never answer content)
     */
    public record CollectedTraits<R>(R response, Map<String, Integer> scores, Map<String, Object> attempts) {
    }

    public static <R> CollectedTraits<R> collect(List<TraitDefinition> traits, Supplier<R> call,
                                                 Function<R, JsonNode> contentOf) {
        List<Map<String, Object>> violations = new ArrayList<>();
        List<String> lastViolation = List.of();
        for (int attempt = 1; attempt <= 1 + MAX_EXTRA_CALLS; attempt++) {
            R response = call.get();
            TraitScoreReader.TraitReading reading = TraitScoreReader.read(contentOf.apply(response), traits);
            if (reading.violations().isEmpty()) {
                return new CollectedTraits<>(response, reading.scores(), attempts(attempt, violations));
            }
            violations.add(Map.of(ATTEMPT, attempt, FIELDS, reading.violations()));
            lastViolation = reading.violations();
        }
        throw new AiProviderException(AiFailureReason.PROVIDER_RESPONSE_INVALID,
                String.format(ScoringConstants.GEMINI_FIELD_INVALID, lastViolation.getFirst()));
    }

    private static Map<String, Object> attempts(int count, List<Map<String, Object>> violations) {
        Map<String, Object> attempts = new LinkedHashMap<>();
        attempts.put(ATTEMPT_COUNT, count);
        attempts.put(ATTEMPT_VIOLATIONS, List.copyOf(violations));
        return attempts;
    }
}
