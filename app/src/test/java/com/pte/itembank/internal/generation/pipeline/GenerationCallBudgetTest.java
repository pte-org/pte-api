package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.exception.GenerationCallBudgetExceededException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationCallBudgetTest {

    @Test
    void runsTheHookBeforeEveryAllowedCallButNotForARefusedOne() {
        AtomicInteger renewals = new AtomicInteger();
        GenerationCallBudget budget = new GenerationCallBudget(2, renewals::incrementAndGet);

        budget.consume();
        budget.consume();

        assertThatThrownBy(budget::consume).isInstanceOf(GenerationCallBudgetExceededException.class);
        assertThat(renewals).hasValue(2);
        assertThat(budget.remaining()).isZero();
    }
}
