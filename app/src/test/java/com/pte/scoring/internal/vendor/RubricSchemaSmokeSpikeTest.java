package com.pte.scoring.internal.vendor;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.rubric.prompt.RubricPromptBuilder;
import com.pte.scoring.internal.vendor.gemini.GeminiRequest;
import com.pte.scoring.internal.vendor.gemini.GeminiResponse;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.rubric.TaskRubric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import static com.pte.scoring.internal.vendor.SpikeSupport.GEMINI_BASE;
import static com.pte.scoring.internal.vendor.SpikeSupport.HTTP;
import static com.pte.scoring.internal.vendor.SpikeSupport.JSON;
import static com.pte.scoring.internal.vendor.SpikeSupport.TIMEOUT;
import static com.pte.scoring.internal.vendor.SpikeSupport.env;

/**
 * Phase 3 schema smoke (plan quang-scoring-rubric-transparency, MEDIUM-5): does real Gemini accept each of three
 * {@code responseSchema} variants for one trait on a 0-6 scale (Write Essay Content), and does it answer with the
 * right type inside the range? Ten calls per variant on one synthetic essay. It spends Gemini quota, so it runs only
 * with {@code SCORING_AI_SPIKE=true} and {@code SCORING_AI_GEMINI_API_KEY}; run one method at a time. Prints counts
 * only, never the essay.
 *
 * <p>Not executed when the phase was implemented (no key on that machine): the production schema uses variant (b),
 * chosen from the Gemini REST responseSchema documentation ({@code enum} is documented for STRING only; INTEGER
 * supports {@code minimum}/{@code maximum}). Java validation of every trait stays mandatory whatever this shows.
 */
@EnabledIfEnvironmentVariable(named = "SCORING_AI_SPIKE", matches = "true")
class RubricSchemaSmokeSpikeTest {

    private static final int RUNS = 10;
    private static final int MAX = 6;
    private static final String FIELD = "CONTENT";
    private static final String ESSAY = "Many cities are considering whether cars should be banned from their "
            + "centres. Supporters argue that fewer cars mean cleaner air and safer streets for pedestrians. "
            + "Opponents reply that shops depend on drivers and that public transport is not ready. In my view, a "
            + "gradual ban, starting with weekends, balances health and business. Cities such as Oslo show that "
            + "investment in buses and cycling lanes keeps the centre lively. Therefore, a careful plan is better "
            + "than an immediate ban.";

    private final GeminiRestClient gemini = new GeminiRestClient(HTTP, JSON, GEMINI_BASE,
            env("SCORING_AI_GEMINI_API_KEY"),
            System.getenv().getOrDefault("SCORING_AI_GEMINI_MODEL", "gemini-flash-lite-latest"), TIMEOUT);

    @Test
    void variantA_integerWithEnum() {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "INTEGER");
        property.put("enum", IntStream.rangeClosed(0, MAX).boxed().toList());
        run("(a) INTEGER enum", property);
    }

    @Test
    void variantB_integerWithMinimumAndMaximum() {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "INTEGER");
        property.put("minimum", 0);
        property.put("maximum", MAX);
        run("(b) INTEGER minimum/maximum", property);
    }

    @Test
    void variantC_stringEnum() {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "STRING");
        property.put("enum", IntStream.rangeClosed(0, MAX).mapToObj(String::valueOf).toList());
        run("(c) STRING enum", property);
    }

    private void run(String label, Map<String, Object> property) {
        TaskRubric essay = new RubricCatalogImpl().find(ScoringConstants.TASK_TYPE_WRITE_ESSAY).orElseThrow();
        String system = "You are a PTE Academic examiner. Score only the CONTENT trait.\n\n"
                + RubricPromptBuilder.rubricSection(essay);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", Map.of(FIELD, property));
        schema.put("required", List.of(FIELD));
        GeminiRequest request = new GeminiRequest(system,
                "Essay prompt:\n\"\"\"\nShould cities ban cars?\n\"\"\"\nCandidate's essay:\n\"\"\"\n" + ESSAY
                        + "\n\"\"\"\n", schema, List.of());
        int accepted = 0;
        int wellTyped = 0;
        int inRange = 0;
        List<String> failures = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (int run = 0; run < RUNS; run++) {
            try {
                GeminiResponse response = gemini.generate(request);
                accepted++;
                JsonNode value = response.content().get(FIELD);
                values.add(String.valueOf(value));
                Integer parsed = parse(value, property);
                if (parsed != null) {
                    wellTyped++;
                    if (parsed >= 0 && parsed <= MAX) {
                        inRange++;
                    }
                }
            } catch (AiProviderException ex) {
                failures.add(ex.getReason().name());
            }
        }
        System.out.printf(Locale.ROOT, "%s: accepted=%d/%d wellTyped=%d inRange=%d values=%s failures=%s%n",
                label, accepted, RUNS, wellTyped, inRange, values, failures);
    }

    /** The integer the variant promises: a JSON integer for (a) and (b), a numeric string for (c). */
    private static Integer parse(JsonNode value, Map<String, Object> property) {
        if (value == null) {
            return null;
        }
        if ("STRING".equals(property.get("type"))) {
            return value.isString() && value.asText("").matches("\\d") ? Integer.valueOf(value.asText("")) : null;
        }
        return value.isIntegralNumber() ? value.asInt() : null;
    }
}
