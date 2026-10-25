package com.pte.practice.internal.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeQuestionPickerTest {

    private final PracticeQuestionPicker picker = new PracticeQuestionPicker(() -> new java.util.Random(7));

    private static List<UUID> ids(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    @Test
    void returnsDistinctQuestionsUpToTheRequestedCount() {
        var candidates = ids(10);
        var picked = picker.pick(candidates, Set.of(), 4);
        assertThat(picked).hasSize(4).doesNotHaveDuplicates().isSubsetOf(candidates);
    }

    @Test
    void neverSeenQuestionsComeBeforeSeenOnes() {
        var candidates = ids(6);
        var seen = Set.copyOf(candidates.subList(0, 4));
        var picked = picker.pick(candidates, seen, 2);
        assertThat(picked).containsExactlyInAnyOrder(candidates.get(4), candidates.get(5));
    }

    @Test
    void fallsBackToSeenQuestionsWhenTheBankIsSmallerThanTheCount() {
        var candidates = ids(3);
        var picked = picker.pick(candidates, Set.copyOf(candidates), 12);
        assertThat(picked).hasSize(3).containsExactlyInAnyOrderElementsOf(candidates);
    }

    @Test
    void emptyInputsAndNonPositiveCountsPickNothing() {
        assertThat(picker.pick(List.of(), Set.of(), 3)).isEmpty();
        assertThat(picker.pick(null, Set.of(), 3)).isEmpty();
        assertThat(picker.pick(ids(3), Set.of(), 0)).isEmpty();
    }

    @Test
    void sameSeedGivesTheSameOrder() {
        var candidates = ids(8);
        var first = new PracticeQuestionPicker(() -> new java.util.Random(1)).pick(candidates, Set.of(), 8);
        var second = new PracticeQuestionPicker(() -> new java.util.Random(1)).pick(candidates, Set.of(), 8);
        assertThat(first).isEqualTo(second);
    }
}
