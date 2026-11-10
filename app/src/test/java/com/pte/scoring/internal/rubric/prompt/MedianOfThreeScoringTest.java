package com.pte.scoring.internal.rubric.prompt;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.vendor.AiProviderException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MedianOfThreeScoringTest {

    private static final String CONTENT = "CONTENT";
    private static final String GRAMMAR = "GRAMMAR";
    private static final String SPELLING = "SPELLING";

    @Test
    void selectedTraitTakesTheMiddleOfThreeWholeNumbersWithoutRounding() {
        Map<String, Integer> combined = MedianOfThreeScoring.combine(List.of(
                traits(5, 2, 2), traits(3, 1, 0), traits(4, 0, 1)), Set.of(CONTENT, GRAMMAR, SPELLING));

        assertThat(combined).containsExactly(Map.entry(CONTENT, 4), Map.entry(GRAMMAR, 1), Map.entry(SPELLING, 1));
    }

    @Test
    void medianIgnoresOneOutlierInEitherDirection() {
        assertThat(MedianOfThreeScoring.combine(List.of(traits(6, 2, 2), traits(0, 2, 2), traits(6, 2, 2)),
                Set.of(CONTENT))).containsEntry(CONTENT, 6);
        assertThat(MedianOfThreeScoring.combine(List.of(traits(1, 2, 2), traits(6, 2, 2), traits(1, 2, 2)),
                Set.of(CONTENT))).containsEntry(CONTENT, 1);
    }

    @Test
    void traitsNotSelectedKeepTheFirstRunsValueAndTheFirstRunsOrder() {
        Map<String, Integer> combined = MedianOfThreeScoring.combine(List.of(
                traits(2, 0, 2), traits(5, 2, 0), traits(6, 2, 0)), Set.of(CONTENT));

        assertThat(combined).containsExactly(Map.entry(CONTENT, 5), Map.entry(GRAMMAR, 0), Map.entry(SPELLING, 2));
    }

    @Test
    void noSelectedTraitReturnsTheFirstRunUnchanged() {
        Map<String, Integer> first = traits(3, 1, 2);

        assertThat(MedianOfThreeScoring.combine(List.of(first, traits(6, 2, 0), traits(0, 0, 0)), Set.of()))
                .isEqualTo(first);
    }

    @Test
    void identicalRunsGiveTheSameScores() {
        Map<String, Integer> run = traits(4, 2, 1);

        assertThat(MedianOfThreeScoring.combine(List.of(run, run, run), Set.of(CONTENT, GRAMMAR, SPELLING)))
                .isEqualTo(run);
    }

    @Test
    void fewerThanThreeRunsFailClosedInsteadOfAveragingTwo() {
        assertFailsClosed(List.of(traits(4, 2, 2), traits(5, 2, 2)), Set.of(CONTENT));
        assertFailsClosed(List.of(), Set.of(CONTENT));
    }

    @Test
    void moreThanThreeRunsAreRejected() {
        assertFailsClosed(List.of(traits(4, 2, 2), traits(4, 2, 2), traits(4, 2, 2), traits(4, 2, 2)),
                Set.of(CONTENT));
    }

    @Test
    void aMissingRunFailsClosed() {
        assertFailsClosed(Arrays.asList(traits(4, 2, 2), null, traits(4, 2, 2)), Set.of(CONTENT));
    }

    @Test
    void aRunWithADifferentTraitSetFailsClosed() {
        Map<String, Integer> missingSpelling = new LinkedHashMap<>();
        missingSpelling.put(CONTENT, 4);
        missingSpelling.put(GRAMMAR, 2);

        assertFailsClosed(List.of(traits(4, 2, 2), missingSpelling, traits(4, 2, 2)), Set.of(CONTENT));
    }

    @Test
    void aRunWithANullTraitValueFailsClosed() {
        Map<String, Integer> nullContent = new LinkedHashMap<>();
        nullContent.put(CONTENT, null);
        nullContent.put(GRAMMAR, 2);
        nullContent.put(SPELLING, 2);

        assertFailsClosed(List.of(traits(4, 2, 2), nullContent, traits(4, 2, 2)), Set.of(CONTENT));
    }

    @Test
    void aSelectedTraitTheRunsDoNotHaveFailsClosed() {
        assertFailsClosed(List.of(traits(4, 2, 2), traits(4, 2, 2), traits(4, 2, 2)), Set.of("VOCABULARY"));
    }

    private static void assertFailsClosed(List<Map<String, Integer>> runs, Set<String> selected) {
        assertThatThrownBy(() -> MedianOfThreeScoring.combine(runs, selected))
                .isInstanceOf(AiProviderException.class)
                .extracting(ex -> ((AiProviderException) ex).getReason())
                .isEqualTo(AiFailureReason.PROVIDER_RESPONSE_INVALID);
    }

    private static Map<String, Integer> traits(int content, int grammar, int spelling) {
        Map<String, Integer> traits = new LinkedHashMap<>();
        traits.put(CONTENT, content);
        traits.put(GRAMMAR, grammar);
        traits.put(SPELLING, spelling);
        return traits;
    }
}
