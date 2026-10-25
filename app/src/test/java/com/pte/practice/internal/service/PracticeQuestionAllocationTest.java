package com.pte.practice.internal.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeQuestionAllocationTest {

    @Test
    void splitsEvenlyAndGivesTheRemainderToTheFirstTypes() {
        assertThat(PracticeQuestionAllocation.distribute(10, 3)).containsExactly(4, 3, 3);
        assertThat(PracticeQuestionAllocation.distribute(9, 3)).containsExactly(3, 3, 3);
        assertThat(PracticeQuestionAllocation.distribute(50, 7)).containsExactly(8, 7, 7, 7, 7, 7, 7);
    }

    @Test
    void fewerQuestionsThanTypesLeavesTheLastTypesEmpty() {
        assertThat(PracticeQuestionAllocation.distribute(2, 3)).containsExactly(1, 1, 0);
    }

    @Test
    void partsAlwaysAddUpToTheTotal() {
        for (int total = 0; total <= 60; total++) {
            for (int types = 1; types <= 9; types++) {
                assertThat(java.util.Arrays.stream(PracticeQuestionAllocation.distribute(total, types)).sum())
                        .isEqualTo(total);
            }
        }
    }

    @Test
    void rejectsNoTypesOrNegativeTotal() {
        assertThatThrownBy(() -> PracticeQuestionAllocation.distribute(5, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PracticeQuestionAllocation.distribute(-1, 2)).isInstanceOf(IllegalArgumentException.class);
    }
}
