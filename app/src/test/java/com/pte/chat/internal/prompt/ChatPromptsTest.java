package com.pte.chat.internal.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Keyword checks are deliberately loose: they pin the rules, not the wording of the prompt. */
class ChatPromptsTest {

    @Test
    void versionIsASingleVNumberConstant() {
        assertThat(ChatPrompts.VERSION).matches("v\\d+");
    }

    @Test
    void theVersionWasBumpedWhenTheTaskWasNoLongerDoneForTheStudent() {
        assertThat(ChatPrompts.VERSION).isEqualTo("v3");
    }

    @Test
    void systemInstructionExplainsHowToUseTheExerciseContextOnlyWhenItIsRelevant() {
        String prompt = ChatPrompts.systemInstruction().toLowerCase();

        assertThat(prompt).contains("exercise");
        assertThat(prompt).containsAnyOf("relevant", "relates", "related");
        assertThat(prompt).containsAnyOf("unrelated", "not related", "otherwise");
    }

    @Test
    void theQuestionIsSentAsItIsWhenThereIsNoExercise() {
        assertThat(ChatPrompts.userTurn("How do I say this?", null)).isEqualTo("How do I say this?");
        assertThat(ChatPrompts.userTurn("How do I say this?", "  ")).isEqualTo("How do I say this?");
    }

    @Test
    void theExerciseGoesBeforeTheQuestionAndIsMarkedAsDataNotInstructions() {
        String turn = ChatPrompts.userTurn("How do I say this?", "Technology makes us less social.");

        assertThat(turn).contains("Technology makes us less social.");
        assertThat(turn.indexOf("Technology makes us less social.")).isLessThan(turn.indexOf("How do I say this?"));
        assertThat(turn.toLowerCase()).containsAnyOf("not instructions", "not as instructions", "context only");
        assertThat(turn).endsWith("How do I say this?");
    }

    @Test
    void systemInstructionIsNonBlankAndWithinTheCostBudget() {
        assertThat(ChatPrompts.systemInstruction()).isNotBlank();
        assertThat(ChatPrompts.systemInstruction().length()).isLessThanOrEqualTo(6000);
    }

    @Test
    void systemInstructionIsStableAcrossCalls() {
        assertThat(ChatPrompts.systemInstruction()).isEqualTo(ChatPrompts.systemInstruction());
    }

    @Test
    void systemInstructionNamesTheExam() {
        assertThat(ChatPrompts.systemInstruction()).contains("PTE");
    }

    @Test
    void systemInstructionTreatsUserContentAsDataAndKeepsItselfPrivate() {
        String prompt = ChatPrompts.systemInstruction().toLowerCase();

        assertThat(prompt).contains("ignore");
        assertThat(prompt).containsAnyOf("reveal", "disclose", "repeat");
        assertThat(prompt).containsAnyOf("data", "untrusted");
    }

    @Test
    void systemInstructionAnswersInTheUsersLanguage() {
        assertThat(ChatPrompts.systemInstruction().toLowerCase()).contains("language");
    }

    @Test
    void systemInstructionStatesTheNeutralNoFlatteryRule() {
        assertThat(ChatPrompts.systemInstruction().toLowerCase()).containsAnyOf("flatter", "praise");
    }

    @ParameterizedTest
    @ValueSource(strings = {"vi", "VI", "Vi"})
    void vietnameseRefusalIsReturnedForVi(String language) {
        String refusal = ChatPrompts.refusal(language);

        assertThat(refusal).isNotBlank().contains("tiếng Anh");
        assertThat(refusal.length()).isLessThanOrEqualTo(300);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"en", "EN", "", "   ", "fr", "xx", "vietnamese"})
    void englishRefusalIsTheDefaultForEnNullBlankAndUnknown(String language) {
        String refusal = ChatPrompts.refusal(language);

        assertThat(refusal).isNotBlank().contains("English");
        assertThat(refusal.length()).isLessThanOrEqualTo(300);
    }

    @Test
    void refusalsAreDifferentLanguagesAndNeverNull() {
        assertThat(ChatPrompts.refusal("vi")).isNotEqualTo(ChatPrompts.refusal("en"));
        assertThat(ChatPrompts.refusal(null)).isNotNull();
        assertThat(ChatPrompts.refusal("vi")).doesNotContain("\n\n\n");
    }
}
