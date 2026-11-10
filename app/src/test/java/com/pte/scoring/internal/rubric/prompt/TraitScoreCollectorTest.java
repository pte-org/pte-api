package com.pte.scoring.internal.rubric.prompt;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TraitDefinition;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fail closed with a bounded retry: a missing, non-numeric, fractional or out-of-scale trait is a schema violation;
 * the same call is repeated at most twice more, nothing is ever rounded or clamped, and every attempt is recorded
 * (field names only).
 */
class TraitScoreCollectorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Summarize Written Text: CONTENT 0-4, GRAMMAR 0-2, VOCABULARY 0-2. */
    private static final List<TraitDefinition> SWT = RubricPromptBuilder.llmTraits(
            new RubricCatalogImpl().find(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT).orElseThrow());
    private static final String VALID = "{\"CONTENT\": 3, \"GRAMMAR\": 2, \"VOCABULARY\": 1}";

    @Test
    void validScoresAreReadOnTheFirstCallAndTheAttemptIsRecorded() {
        Calls calls = new Calls(VALID);

        TraitScoreCollector.CollectedTraits<JsonNode> collected = collect(calls);

        assertThat(collected.scores()).containsExactly(Map.entry(RubricTraits.CONTENT, 3),
                Map.entry(RubricTraits.GRAMMAR, 2), Map.entry(RubricTraits.VOCABULARY, 1));
        assertThat(calls.made).isEqualTo(1);
        assertThat(collected.attempts()).containsEntry("count", 1).containsEntry("violations", List.of());
    }

    @Test
    void aWholeNumberWrittenWithADecimalPointIsStillThatNumber() {
        TraitScoreCollector.CollectedTraits<JsonNode> collected =
                collect(new Calls("{\"CONTENT\": 4.0, \"GRAMMAR\": 0, \"VOCABULARY\": 2}"));

        assertThat(collected.scores()).containsEntry(RubricTraits.CONTENT, 4);
    }

    @Test
    void aValueAboveTheScaleIsRetriedTwiceThenFailsClosedWithoutClamping() {
        assertFailsAfterThreeCalls("{\"CONTENT\": 7, \"GRAMMAR\": 2, \"VOCABULARY\": 1}", RubricTraits.CONTENT);
    }

    @Test
    void aFractionalValueIsRetriedTwiceThenFailsClosedWithoutRounding() {
        assertFailsAfterThreeCalls("{\"CONTENT\": 3.5, \"GRAMMAR\": 2, \"VOCABULARY\": 1}", RubricTraits.CONTENT);
    }

    @Test
    void aMissingTraitIsRetriedTwiceThenFailsClosedWithoutDefaulting() {
        assertFailsAfterThreeCalls("{\"CONTENT\": 3, \"VOCABULARY\": 1}", RubricTraits.GRAMMAR);
    }

    @Test
    void aNegativeOrTextualValueIsAViolation() {
        assertFailsAfterThreeCalls("{\"CONTENT\": -1, \"GRAMMAR\": 2, \"VOCABULARY\": 1}", RubricTraits.CONTENT);
        assertFailsAfterThreeCalls("{\"CONTENT\": \"3\", \"GRAMMAR\": 2, \"VOCABULARY\": 1}", RubricTraits.CONTENT);
        assertFailsAfterThreeCalls("{\"CONTENT\": null, \"GRAMMAR\": 2, \"VOCABULARY\": 1}", RubricTraits.CONTENT);
    }

    @Test
    void aValidSecondAttemptSucceedsAndTheViolationOfTheFirstIsRecorded() {
        Calls calls = new Calls("{\"CONTENT\": 7, \"GRAMMAR\": 2.5, \"VOCABULARY\": 1}", VALID);

        TraitScoreCollector.CollectedTraits<JsonNode> collected = collect(calls);

        assertThat(calls.made).isEqualTo(2);
        assertThat(collected.scores()).containsEntry(RubricTraits.CONTENT, 3);
        assertThat(collected.response().path("CONTENT").asInt()).isEqualTo(3);
        assertThat(collected.attempts()).containsEntry("count", 2).containsEntry("violations",
                List.of(Map.of("attempt", 1, "fields", List.of(RubricTraits.CONTENT, RubricTraits.GRAMMAR))));
    }

    @Test
    void aValidThirdAttemptIsTheLastOneAllowed() {
        Calls calls = new Calls("{}", "{\"CONTENT\": 9}", VALID);

        TraitScoreCollector.CollectedTraits<JsonNode> collected = collect(calls);

        assertThat(calls.made).isEqualTo(1 + TraitScoreCollector.MAX_EXTRA_CALLS);
        assertThat(collected.attempts()).containsEntry("count", 3);
        assertThat((List<?>) collected.attempts().get("violations")).hasSize(2);
    }

    @Test
    void aProviderFailureIsNotRetriedHere() {
        Calls calls = new Calls();

        assertThatThrownBy(() -> TraitScoreCollector.collect(SWT, () -> {
            calls.made++;
            throw new AiProviderException(AiFailureReason.PROVIDER_CONTENT_BLOCKED, "blocked");
        }, Function.identity())).isInstanceOfSatisfying(AiProviderException.class,
                ex -> assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_CONTENT_BLOCKED));
        assertThat(calls.made).isEqualTo(1);
    }

    private static void assertFailsAfterThreeCalls(String response, String field) {
        Calls calls = new Calls(response, response, response);

        assertThatThrownBy(() -> collect(calls)).isInstanceOfSatisfying(AiProviderException.class, ex -> {
            assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_RESPONSE_INVALID);
            assertThat(ex.isTransient()).isFalse();
            assertThat(ex.getMessage()).isEqualTo(String.format(ScoringConstants.GEMINI_FIELD_INVALID, field));
        });
        assertThat(calls.made).isEqualTo(3);
    }

    private static TraitScoreCollector.CollectedTraits<JsonNode> collect(Calls calls) {
        return TraitScoreCollector.collect(SWT, calls::next, Function.identity());
    }

    /** Hands out the given responses in order and counts the calls. */
    private static final class Calls {
        private final Deque<String> responses = new ArrayDeque<>();
        private int made;

        Calls(String... responses) {
            this.responses.addAll(List.of(responses));
        }

        JsonNode next() {
            made++;
            return JSON.readTree(responses.removeFirst());
        }
    }
}
