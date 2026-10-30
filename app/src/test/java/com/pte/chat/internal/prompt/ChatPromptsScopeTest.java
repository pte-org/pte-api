package com.pte.chat.internal.prompt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loose keyword checks on the scope wording of the system instruction. They cannot prove the model behaves; the
 * real behaviour is measured with the prompt suite in phase 5. They stop the agreed wording from silently going.
 */
class ChatPromptsScopeTest {

    private static final String PROMPT = ChatPrompts.systemInstruction();

    @Test
    void theStudentIsHelpedToDoTheTaskButTheTaskIsNotDoneForThem() {
        String lower = PROMPT.toLowerCase();

        assertThat(lower).contains("do not write");
        assertThat(lower).contains("complete");
        assertThat(lower).contains("outline");
        assertThat(lower).containsAnyOf("hint", "hints");
        assertThat(lower).contains("own attempt");
    }

    @Test
    void templatesAndFeedbackOnTheStudentsOwnWritingStayAllowed() {
        String lower = PROMPT.toLowerCase();

        assertThat(lower).contains("templates");
        assertThat(lower).contains("own essay");
        assertThat(lower).contains("correcting or improving the student's own");
    }

    @Test
    void aFullModelAnswerIsNoLongerOffered() {
        assertThat(PROMPT.toLowerCase()).doesNotContain("model answers, sample responses");
    }

    @Test
    void correctionAnswersAreLimitedInLengthAndInNumberOfListedErrors() {
        assertThat(PROMPT).contains("350");
        assertThat(PROMPT.toLowerCase()).contains("five");
    }

    @Test
    void opinionsAreOutOfScopeOnlyWhenUnrelatedToEnglish() {
        assertThat(PROMPT).contains("unrelated to English");
        assertThat(PROMPT).doesNotContain("news, opinions");
        assertThat(PROMPT).doesNotContain("opinions, health");
    }

    @Test
    void theInstructionStaysWithinTheTokenBudget() {
        assertThat(PROMPT.length()).isLessThanOrEqualTo(6000);
    }
}
