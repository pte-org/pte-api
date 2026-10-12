package com.pte.scoring.internal.vendor.speech;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerKeyTest {

    @Test
    void acceptedAnswersSplitOnSlashesCanonicalFirstAndDropDuplicates() {
        AnswerKey key = AnswerKey.parse("astronomer / an astronomer /Astronomer/ ");

        assertThat(key.acceptedAnswers()).containsExactly("astronomer", "an astronomer");
    }

    @Test
    void canonicalAnswerAndAliasBothMatchAfterNormalization() {
        AnswerKey key = AnswerKey.parse("astronomer / an astronomer");

        assertThat(key.match("Astronomer.")).contains("astronomer");
        assertThat(key.match("An astronomer!")).contains("an astronomer");
    }

    @Test
    void theWholeTokenSequenceMustMatchNotJustAPart() {
        AnswerKey key = AnswerKey.parse("cheap / inexpensive");

        assertThat(key.match("it is cheap")).isEmpty();
        assertThat(key.match("cheap cheap")).isEmpty();
        assertThat(key.match("")).isEmpty();
    }

    @Test
    void aSingleAnswerWithoutSlashesStillWorks() {
        assertThat(AnswerKey.parse("seven").match("Seven")).contains("seven");
        assertThat(AnswerKey.parse("seven").match("7")).isEmpty();
    }

    @Test
    void aBlankOrPunctuationOnlyKeyHasNothingToMatch() {
        assertThat(AnswerKey.parse(null).acceptedAnswers()).isEmpty();
        assertThat(AnswerKey.parse(" / ... / ").acceptedAnswers()).isEmpty();
        assertThat(AnswerKey.parse("").match("anything")).isEmpty();
    }
}
