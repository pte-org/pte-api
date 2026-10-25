package com.pte.scoring.internal.service;

import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.scoring.domain.enums.ScoringMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The scoring method comes from the runtime contract frozen on the exam item, never from a catalog lookup. */
class ScoringMethodsTest {

    @Test
    void speakingTextAndObjectiveItemsResolveFromTheirOwnRuntime() {
        assertThat(ScoringMethods.of(TaskRuntimeProfileRegistry.descriptorFor("READ_ALOUD")))
                .contains(ScoringMethod.AI_SPEECH);
        assertThat(ScoringMethods.of(TaskRuntimeProfileRegistry.descriptorFor("WRITE_ESSAY")))
                .contains(ScoringMethod.AI_TEXT);
        assertThat(ScoringMethods.of(TaskRuntimeProfileRegistry.descriptorFor("MC_READING_SINGLE")))
                .contains(ScoringMethod.OBJECTIVE);
        assertThat(ScoringMethods.of(TaskRuntimeProfileRegistry.descriptorFor("PERSONAL_INTRODUCTION")))
                .contains(ScoringMethod.UNSCORED);
    }

    @Test
    void anItemWithoutARuntimeHasNoMethod() {
        assertThat(ScoringMethods.of(null)).isEmpty();
        assertThat(ScoringMethods.isAiScored(null)).isFalse();
    }

    @Test
    void onlySpeechAndTextAreAiScored() {
        assertThat(ScoringMethods.isAiScored(TaskRuntimeProfileRegistry.descriptorFor("READ_ALOUD"))).isTrue();
        assertThat(ScoringMethods.isAiScored(TaskRuntimeProfileRegistry.descriptorFor("MC_READING_SINGLE"))).isFalse();
    }
}
