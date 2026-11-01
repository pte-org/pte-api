package com.pte.assessment.internal.service;

import com.pte.assessment.dto.request.NormalizeSkillWeightsRequest.Item;
import com.pte.assessment.dto.response.SkillWeightSuggestionResponse.SkillSuggestion;
import com.pte.assessment.dto.response.SkillWeightSuggestionResponse.TaskWeight;
import com.pte.assessment.internal.service.definition.SkillWeightNormalizer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillWeightNormalizerTest {

    private static Item item(String key, String section, String sp, String wr, String rd, String li) {
        return new Item(key, section, new BigDecimal(sp), new BigDecimal(wr), new BigDecimal(rd),
                new BigDecimal(li));
    }

    private static SkillSuggestion skill(List<Item> items, String name) {
        return SkillWeightNormalizer.suggest(items).skills().stream()
                .filter(s -> s.skill().equals(name)).findFirst().orElseThrow();
    }

    private static BigDecimal sum(List<TaskWeight> weights) {
        return weights.stream().map(TaskWeight::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal weightOf(List<TaskWeight> weights, String key) {
        return weights.stream().filter(w -> w.taskTypeKey().equals(key)).findFirst().orElseThrow().weight();
    }

    @Test
    void balancedColumnsHaveNoSuggestion() {
        List<Item> items = List.of(item("A", "SPEAKING", "60", "0", "0", "0"),
                item("B", "SPEAKING", "40", "0", "0", "0"));

        SkillSuggestion speaking = skill(items, "SPEAKING");

        assertThat(speaking.balanced()).isTrue();
        assertThat(speaking.proportional()).isEmpty();
        // WRITING/READING/LISTENING have no tasks in their section and no weight: also balanced.
        assertThat(skill(items, "READING").balanced()).isTrue();
    }

    @Test
    void proportionalKeepsTheOldRatioAndTotals100() {
        // READING was 50/30/20 and the 20 task was removed -> 50/30 (total 80).
        List<Item> items = List.of(item("A", "READING", "0", "0", "50", "0"),
                item("B", "READING", "0", "0", "30", "0"));

        SkillSuggestion reading = skill(items, "READING");

        assertThat(reading.balanced()).isFalse();
        assertThat(reading.deficit()).isEqualByComparingTo("20");
        assertThat(weightOf(reading.proportional(), "A")).isEqualByComparingTo("62.50");
        assertThat(weightOf(reading.proportional(), "B")).isEqualByComparingTo("37.50");
        assertThat(sum(reading.proportional())).isEqualByComparingTo("100");
    }

    @Test
    void equalSplitAddsTheSameShareToEveryWeightedTask() {
        List<Item> items = List.of(item("A", "READING", "0", "0", "50", "0"),
                item("B", "READING", "0", "0", "30", "0"));

        SkillSuggestion reading = skill(items, "READING");

        assertThat(weightOf(reading.equal(), "A")).isEqualByComparingTo("60");
        assertThat(weightOf(reading.equal(), "B")).isEqualByComparingTo("40");
        assertThat(sum(reading.equal())).isEqualByComparingTo("100");
    }

    @Test
    void roundingRemainderLandsOnTheLargestWeightSoColumnIsExactly100() {
        List<Item> items = List.of(item("A", "READING", "0", "0", "20", "0"),
                item("B", "READING", "0", "0", "20", "0"),
                item("C", "READING", "0", "0", "20", "0"));

        SkillSuggestion reading = skill(items, "READING");

        assertThat(sum(reading.proportional())).isEqualByComparingTo("100");
        assertThat(sum(reading.equal())).isEqualByComparingTo("100");
    }

    @Test
    void tasksWithoutWeightInTheColumnAreNeverGivenAny() {
        // Writing tasks carry the Reading weight; the section-less task C has none and must stay out.
        List<Item> items = List.of(item("A", "WRITING", "0", "50", "70", "0"),
                item("B", "WRITING", "0", "50", "10", "0"),
                item("C", "WRITING", "0", "0", "0", "0"));

        SkillSuggestion reading = skill(items, "READING");

        assertThat(reading.proportional()).extracting(TaskWeight::taskTypeKey).containsExactly("A", "B");
        assertThat(reading.equal()).extracting(TaskWeight::taskTypeKey).containsExactly("A", "B");
    }

    @Test
    void columnWithNoWeightFallsBackToTheTasksOfItsOwnSection() {
        List<Item> items = List.of(item("A", "LISTENING", "0", "0", "0", "0"),
                item("B", "LISTENING", "0", "0", "0", "0"),
                item("C", "SPEAKING", "100", "0", "0", "0"));

        SkillSuggestion listening = skill(items, "LISTENING");

        assertThat(listening.balanced()).isFalse();
        assertThat(weightOf(listening.proportional(), "A")).isEqualByComparingTo("50");
        assertThat(weightOf(listening.equal(), "B")).isEqualByComparingTo("50");
    }

    @Test
    void overweightColumnIsScaledDownWithoutGoingNegative() {
        List<Item> items = List.of(item("A", "READING", "0", "0", "90", "0"),
                item("B", "READING", "0", "0", "10", "0"),
                item("C", "READING", "0", "0", "20", "0"));

        SkillSuggestion reading = skill(items, "READING");

        assertThat(reading.deficit()).isEqualByComparingTo("-20");
        assertThat(reading.proportional()).allSatisfy(w -> assertThat(w.weight().signum()).isGreaterThanOrEqualTo(0));
        assertThat(reading.equal()).allSatisfy(w -> assertThat(w.weight().signum()).isGreaterThanOrEqualTo(0));
        assertThat(sum(reading.proportional())).isEqualByComparingTo("100");
        assertThat(sum(reading.equal())).isEqualByComparingTo("100");
    }
}
