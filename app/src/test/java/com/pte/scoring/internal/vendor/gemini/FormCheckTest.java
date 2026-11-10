package com.pte.scoring.internal.vendor.gemini;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.rubric.FormRule;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.TextMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FormCheck only counts (words, sentences, capitals, punctuation, bullet points); the band is decided by the
 * catalog's {@link FormRule#bandFor}. The band cases below run the counted metrics through the catalog rule of each
 * writing task, so a count change and a band change are both caught here.
 */
class FormCheckTest {

    private static final RubricCatalog CATALOG = new RubricCatalogImpl();

    @ParameterizedTest
    @CsvSource({"4,0", "5,1", "75,1", "76,0"})
    void summarizeWrittenTextEarnsFormOneForFiveToSeventyFiveWordsInOneSentence(int words, int band) {
        assertThat(band(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT, oneSentence(words))).isEqualTo(band);
    }

    @Test
    void summarizeWrittenTextInTwoSentencesHasFormZero() {
        TextMetrics metrics = FormCheck.measure("The first idea is clear. The second idea follows.");

        assertThat(metrics.sentenceCount()).isEqualTo(2);
        assertThat(rule(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT).bandFor(metrics)).isZero();
    }

    @Test
    void summarizeWrittenTextCountsAnAbbreviationAsPartOfTheSentence() {
        String text = "Dr. Smith argued that e.g. solar power is cheaper than U.S. coal today.";

        assertThat(FormCheck.measure(text).sentenceCount()).isEqualTo(1);
        assertThat(band(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT, text)).isEqualTo(1);
    }

    @Test
    void anAcronymWithDotsIsNotSplitIntoSentences() {
        assertThat(FormCheck.countSentences("Exports to the U.S.A. rose while imports to the U.K. fell.")).isEqualTo(1);
        assertThat(FormCheck.countSentences("Work runs 9 a.m. to 5 p.m. daily. Rest follows.")).isEqualTo(2);
    }

    @Test
    void aSentenceWithoutClosingPunctuationStillCountsAsOneButHasNoPunctuation() {
        TextMetrics metrics = FormCheck.measure("Solar power is getting cheaper every single year");

        assertThat(metrics.sentenceCount()).isEqualTo(1);
        assertThat(metrics.hasPunctuation()).isFalse();
        // Pearson: a response with no punctuation earns Form 0 (Score Guide pp.33, 35, 42).
        assertThat(rule(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT).bandFor(metrics)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"119,0", "120,1", "199,1", "200,2", "300,2", "301,1", "380,1", "381,0"})
    void essayFormFollowsThePearsonLengthBands(int words, int band) {
        TextMetrics metrics = FormCheck.measure(prose(words));

        assertThat(metrics.wordCount()).isEqualTo(words);
        assertThat(rule(ScoringConstants.TASK_TYPE_WRITE_ESSAY).bandFor(metrics)).isEqualTo(band);
    }

    @ParameterizedTest
    @CsvSource({"39,0", "40,1", "49,1", "50,2", "70,2", "71,1", "100,1", "101,0"})
    void summarizeSpokenTextFormFollowsThePearsonLengthBands(int words, int band) {
        assertThat(band(ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT, prose(words))).isEqualTo(band);
    }

    @Test
    void anAllCapitalResponseIsMeasuredAsSuchAndHasFormZero() {
        String shouting = prose(250).toUpperCase(java.util.Locale.ROOT);

        assertThat(FormCheck.measure(shouting).allCaps()).isTrue();
        assertThat(FormCheck.measure(prose(250)).allCaps()).isFalse();
        assertThat(FormCheck.measure("NASA and the ESA launched it.").allCaps()).isFalse();
        assertThat(band(ScoringConstants.TASK_TYPE_WRITE_ESSAY, shouting)).isZero();
        assertThat(band(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT, "SOLAR POWER IS GETTING CHEAPER NOW."))
                .isZero();
    }

    @Test
    void aResponseMadeOnlyOfBulletPointsIsMeasuredAsSuchAndHasFormZero() {
        String bullets = ("- " + prose(10) + "\n").repeat(6) + "* " + prose(10) + "\n1. " + prose(10) + "\n2) "
                + prose(10);

        assertThat(FormCheck.measure(bullets).bulletOnly()).isTrue();
        assertThat(band(ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT, bullets)).isZero();
        assertThat(FormCheck.measure("Intro line.\n- " + prose(10)).bulletOnly()).isFalse();
    }

    @Test
    void aResponseMadeOnlyOfVeryShortSentencesCountsAsBulletLike() {
        // 64 words: inside the SST full-form length, so only the short-sentence rule can zero it.
        String choppy = "Cars pollute. Bikes help. Buses too. Trains work. ".repeat(8).trim();

        assertThat(FormCheck.measure(choppy).bulletOnly()).isTrue();
        assertThat(band(ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT, choppy)).isZero();
        assertThat(FormCheck.measure("Solar power is getting cheaper every year.").bulletOnly()).isFalse();
    }

    @Test
    void wordsAreSeparatedByAnyWhitespace() {
        assertThat(FormCheck.countWords("one  two\tthree\nfour five")).isEqualTo(5);
        assertThat(FormCheck.countWords("   ")).isZero();
        assertThat(FormCheck.measure(null).wordCount()).isZero();
    }

    @Test
    void theDetailRecordsTheMetricsTheBandAndTheRuleButNeverTheText() {
        String essay = prose(150);
        TextMetrics metrics = FormCheck.measure(essay);
        FormRule rule = rule(ScoringConstants.TASK_TYPE_WRITE_ESSAY);

        Map<String, Object> detail = FormCheck.detail(metrics, rule, rule.bandFor(metrics));

        assertThat(detail).containsEntry("wordCount", 150).containsEntry("band", 1).containsEntry("maxBand", 2)
                .containsEntry("allCaps", false).containsEntry("hasPunctuation", true)
                .containsEntry("bulletOnly", false).containsEntry("requireSingleSentence", false)
                .containsEntry("mechanicalRules", true).containsKey("sentenceCount");
        assertThat(detail.toString()).doesNotContain("word word");
    }

    private static int band(String taskType, String text) {
        return rule(taskType).bandFor(FormCheck.measure(text));
    }

    private static FormRule rule(String taskType) {
        return CATALOG.find(taskType).orElseThrow().formRule();
    }

    private static String words(int count) {
        return "word ".repeat(count).trim();
    }

    private static String oneSentence(int count) {
        return words(count) + ".";
    }

    /** Ordinary prose: sentences of up to ten words, each closed by a full stop. */
    static String prose(int count) {
        StringBuilder text = new StringBuilder();
        int left = count;
        while (left > 0) {
            int sentence = Math.min(10, left);
            text.append(text.isEmpty() ? "" : " ").append(words(sentence)).append('.');
            left -= sentence;
        }
        return text.toString();
    }
}
