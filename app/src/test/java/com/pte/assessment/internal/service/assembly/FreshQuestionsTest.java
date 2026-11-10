package com.pte.assessment.internal.service.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FreshQuestionsTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @Test
    void withoutAPreferenceThePoolIsUntouchedAndNothingIsCounted() {
        FreshQuestions none = FreshQuestions.none();

        assertThat(none.isActive()).isFalse();
        assertThat(none.prefer(List.of(a, b), id -> id, 2)).containsExactly(a, b);
        assertThat(none.repeatedIn(List.of(a))).isNull();
    }

    @Test
    void dropsUsedQuestionsWhileEnoughUnusedOnesRemain() {
        FreshQuestions fresh = FreshQuestions.avoiding(Set.of(a));

        assertThat(fresh.prefer(List.of(a, b, c), id -> id, 2)).containsExactly(b, c);
    }

    @Test
    void fallsBackToTheWholePoolWhenTooFewUnusedQuestionsRemain() {
        FreshQuestions fresh = FreshQuestions.avoiding(Set.of(a, b));

        assertThat(fresh.prefer(List.of(a, b, c), id -> id, 2)).containsExactly(a, b, c);
    }

    @Test
    void countsHowManyPickedQuestionsWereUsedBefore() {
        FreshQuestions fresh = FreshQuestions.avoiding(Set.of(a, b));

        assertThat(fresh.repeatedIn(List.of(a, c, d))).isEqualTo(1);
    }
}
