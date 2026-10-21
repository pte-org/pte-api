package com.pte.scoring.internal.vendor.gemini;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FormCheckTest {

    @ParameterizedTest
    @CsvSource({"4,false", "5,true", "75,true", "76,false"})
    void summarizeWrittenTextAcceptsFiveToSeventyFiveWordsInOneSentence(int words, boolean passed) {
        assertThat(FormCheck.summarizeWrittenText(oneSentence(words), null, null).passed()).isEqualTo(passed);
    }

    @Test
    void summarizeWrittenTextNeedsExactlyOneSentence() {
        FormCheck form = FormCheck.summarizeWrittenText("The first idea is clear. The second idea follows.",
                null, null);

        assertThat(form.sentenceCount()).isEqualTo(2);
        assertThat(form.passed()).isFalse();
        assertThat(form.message()).contains("sentence count 2");
    }

    @Test
    void summarizeWrittenTextCountsAnAbbreviationAsPartOfTheSentence() {
        FormCheck form = FormCheck.summarizeWrittenText(
                "Dr. Smith argued that e.g. solar power is cheaper than U.S. coal today.", null, null);

        assertThat(form.sentenceCount()).isEqualTo(1);
        assertThat(form.passed()).isTrue();
    }

    @Test
    void anAcronymWithDotsIsNotSplitIntoSentences() {
        assertThat(FormCheck.countSentences("Exports to the U.S.A. rose while imports to the U.K. fell.")).isEqualTo(1);
        assertThat(FormCheck.countSentences("Work runs 9 a.m. to 5 p.m. daily. Rest follows.")).isEqualTo(2);
    }

    @Test
    void aSentenceWithoutClosingPunctuationStillCountsAsOne() {
        assertThat(FormCheck.summarizeWrittenText("Solar power is getting cheaper every single year", null, null)
                .sentenceCount()).isEqualTo(1);
    }

    @Test
    void theQuestionsOwnWordLimitsReplaceTheDefaults() {
        assertThat(FormCheck.summarizeWrittenText(oneSentence(30), null, 25).passed()).isFalse();
        assertThat(FormCheck.summarizeWrittenText(oneSentence(30), 10, 30).passed()).isTrue();
        assertThat(FormCheck.summarizeWrittenText(oneSentence(8), 10, 30).passed()).isFalse();
    }

    @Test
    void aLimitOfZeroOrLessFallsBackToTheDefault() {
        assertThat(FormCheck.summarizeWrittenText(oneSentence(80), 0, 0).passed()).isFalse();
        assertThat(FormCheck.essay(words(250), -1, -1).passed()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"199,false", "200,true", "300,true", "301,false"})
    void essayAcceptsTwoHundredToThreeHundredWords(int words, boolean passed) {
        FormCheck form = FormCheck.essay(words(words), null, null);

        assertThat(form.passed()).isEqualTo(passed);
        assertThat(form.sentenceCount()).isNull();
        assertThat(form.wordCount()).isEqualTo(words);
    }

    @Test
    void essayUsesTheQuestionsLimits() {
        assertThat(FormCheck.essay(words(120), 100, 150).passed()).isTrue();
        assertThat(FormCheck.essay(words(160), 100, 150).passed()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "39,invalid_boundary,false", "40,outside_full_range,false", "49,outside_full_range,false",
            "50,in_range,true", "70,in_range,true", "71,outside_full_range,false",
            "100,outside_full_range,false", "101,invalid_boundary,false"})
    void summarizeSpokenTextSeparatesTheFullRangeFromTheInvalidBoundary(int words, String status, boolean passed) {
        FormCheck form = FormCheck.summarizeSpokenText(words(words), null, null);

        assertThat(form.status()).isEqualTo(status);
        assertThat(form.passed()).isEqualTo(passed);
        assertThat(form.score()).isEqualTo(passed ? 100 : 0);
    }

    @Test
    void summarizeSpokenTextUsesTheQuestionsRangeButKeepsTheInvalidBoundaryWide() {
        assertThat(FormCheck.summarizeSpokenText(words(90), 80, 100).status()).isEqualTo("in_range");
        assertThat(FormCheck.summarizeSpokenText(words(105), 80, 120).status()).isEqualTo("in_range");
        assertThat(FormCheck.summarizeSpokenText(words(125), 80, 120).status()).isEqualTo("invalid_boundary");
        assertThat(FormCheck.summarizeSpokenText(words(35), 30, 40).status()).isEqualTo("in_range");
    }

    @Test
    void wordsAreSeparatedByAnyWhitespace() {
        assertThat(FormCheck.countWords("one  two\tthree\nfour five")).isEqualTo(5);
        assertThat(FormCheck.countWords("   ")).isZero();
    }

    @Test
    void theDetailRecordsTheCountsAndTheOutcome() {
        FormCheck form = FormCheck.essay(words(150), null, null);

        assertThat(form.detail()).containsEntry("wordCount", 150).containsEntry("passed", false)
                .containsEntry("status", "outside_full_range").containsKey("message");
    }

    private static String words(int count) {
        return "word ".repeat(count).trim();
    }

    private static String oneSentence(int count) {
        return words(count) + ".";
    }
}
