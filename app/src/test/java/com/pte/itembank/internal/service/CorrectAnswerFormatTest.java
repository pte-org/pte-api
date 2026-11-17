package com.pte.itembank.internal.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorrectAnswerFormatTest {

    @Test
    void typeInNeedsAJsonArrayOfNonBlankStringsWithoutCommas() {
        String key = "FILL_IN_THE_BLANKS_TYPE_IN";
        assertThat(CorrectAnswerFormat.isValid(key, "[\"London\", \"ecosystem\"]")).isTrue();
        assertThat(CorrectAnswerFormat.isValid(key, "London, ecosystem")).isFalse();
        assertThat(CorrectAnswerFormat.isValid(key, "[]")).isFalse();
        assertThat(CorrectAnswerFormat.isValid(key, "[\"\"]")).isFalse();
        assertThat(CorrectAnswerFormat.isValid(key, "[\"a,b\"]")).isFalse();
        assertThat(CorrectAnswerFormat.isValid(key, "[1, 2]")).isFalse();
        assertThat(CorrectAnswerFormat.isValid(key, "{\"a\":1}")).isFalse();
    }

    @Test
    void highlightIncorrectWordsNeedsDistinctNonNegativeIntegers() {
        String key = "HIGHLIGHT_INCORRECT_WORDS";
        assertThat(CorrectAnswerFormat.isValid(key, "[3, 8, 14]")).isTrue();
        assertThat(CorrectAnswerFormat.isValid(key, "[0]")).isTrue();
        assertThat(CorrectAnswerFormat.isValid(key, "[3, 3]")).isFalse();
        assertThat(CorrectAnswerFormat.isValid(key, "[-1]")).isFalse();
        assertThat(CorrectAnswerFormat.isValid(key, "[3.5]")).isFalse();
        assertThat(CorrectAnswerFormat.isValid(key, "[\"3\"]")).isFalse();
        assertThat(CorrectAnswerFormat.isValid(key, "three, five")).isFalse();
        assertThat(CorrectAnswerFormat.isValid(key, "[]")).isFalse();
    }

    @Test
    void shortQuestionNeedsAtLeastOneAnswerWithAWord() {
        String key = "ANSWER_SHORT_QUESTION";
        assertThat(CorrectAnswerFormat.isValid(key, "thermometer / a thermometer")).isTrue();
        assertThat(CorrectAnswerFormat.isValid(key, "seven")).isTrue();
        assertThat(CorrectAnswerFormat.isValid(key, " / ")).isFalse();
        assertThat(CorrectAnswerFormat.isValid(key, "...")).isFalse();
    }

    @Test
    void otherTaskTypesAndMissingKeysAreNotChecked() {
        assertThat(CorrectAnswerFormat.isValid("WRITE_FROM_DICTATION", "Any sentence, really.")).isTrue();
        assertThat(CorrectAnswerFormat.isValid(null, "anything")).isTrue();
    }
}
