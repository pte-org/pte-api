package com.pte.scoring.internal.vendor.speech;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerKeyTest {

    @Test
    void acceptedAnswersSplitOnSlashesCanonicalFirstAndDropDuplicates() {
        AnswerKey key = AnswerKey.parse("astronomer / stargazer /Astronomer/ ");

        assertThat(key.acceptedAnswers()).containsExactly("astronomer", "stargazer");
    }

    @Test
    void canonicalAnswerAndAliasBothMatchAfterNormalization() {
        AnswerKey key = AnswerKey.parse("astronomer / stargazer");

        assertThat(key.match("Astronomer.")).contains("astronomer");
        assertThat(key.match("Stargazer!")).contains("stargazer");
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

    @Test
    void aLeadingArticleIsIgnoredOnBothSides() {
        assertThat(AnswerKey.parse("thermometer").match("A thermometer")).contains("thermometer");
        assertThat(AnswerKey.parse("thermometer").match("the thermometer.")).contains("thermometer");
        assertThat(AnswerKey.parse("an astronomer").match("astronomer")).contains("an astronomer");
        // An alias that differs from the canonical answer only by an article is redundant and collapses.
        assertThat(AnswerKey.parse("astronomer / an astronomer").acceptedAnswers()).containsExactly("astronomer");
    }

    @Test
    void articlesInsideAnAnswerAndAnArticleOnlyAnswerAreKept() {
        assertThat(AnswerKey.parse("hammer and the nail").match("hammer and nail")).isEmpty();
        assertThat(AnswerKey.parse("the hammer and the nail").match("a hammer and the nail"))
                .contains("the hammer and the nail");
        assertThat(AnswerKey.parse("a").match("a")).contains("a");
        assertThat(AnswerKey.parse("a").match("the")).isEmpty();
    }
}
