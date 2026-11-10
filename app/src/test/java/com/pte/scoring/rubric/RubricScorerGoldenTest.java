package com.pte.scoring.rubric;

import com.pte.scoring.internal.constant.RubricConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static com.pte.scoring.rubric.RubricTestSupport.assertRubricError;
import static com.pte.scoring.rubric.RubricTestSupport.effective;
import static com.pte.scoring.rubric.RubricTestSupport.metrics;
import static com.pte.scoring.rubric.RubricTestSupport.scores;
import static com.pte.scoring.rubric.RubricTestSupport.set;
import static com.pte.scoring.rubric.RubricTestSupport.withGates;
import static com.pte.scoring.rubric.RubricTestSupport.withWeights;
import static com.pte.scoring.rubric.RubricTestSupport.withoutWeight;
import static org.assertj.core.api.Assertions.assertThat;

/** Pearson Score Guide worked examples run through the one pure scoring function. */
class RubricScorerGoldenTest {

    private static final String ESSAY = "WRITE_ESSAY";
    private static final String DI = "DESCRIBE_IMAGE";
    private static final String SST = "SUMMARIZE_SPOKEN_TEXT";
    private static final String SWT = "SUMMARIZE_WRITTEN_TEXT";
    private static final String RA = "READ_ALOUD";

    private static Map<String, Integer> essayScores(int form, int content) {
        return scores(RubricTraits.CONTENT, content, RubricTraits.FORM, form,
                RubricTraits.DEVELOPMENT_STRUCTURE_COHERENCE, 4, RubricTraits.GRAMMAR, 1,
                RubricTraits.GENERAL_LINGUISTIC_RANGE, 4, RubricTraits.VOCABULARY_RANGE, 1, RubricTraits.SPELLING, 1);
    }

    private static Map<String, Integer> diScores(int content, int pron, int fluency) {
        return scores(RubricTraits.CONTENT, content, RubricTraits.PRONUNCIATION, pron,
                RubricTraits.ORAL_FLUENCY, fluency);
    }

    private static Map<String, Integer> sstScores(int content, int form) {
        return scores(RubricTraits.CONTENT, content, RubricTraits.FORM, form, RubricTraits.GRAMMAR, 2,
                RubricTraits.VOCABULARY, 2, RubricTraits.SPELLING, 2);
    }

    private static Map<String, Integer> swtScores(int form) {
        return scores(RubricTraits.CONTENT, 4, RubricTraits.FORM, form, RubricTraits.GRAMMAR, 2,
                RubricTraits.VOCABULARY, 2);
    }

    private static TraitScoreSet readAloudScores(int words, int content, int pronunciation, int fluency) {
        return new TraitScoreSet(scores(RubricTraits.CONTENT, content, RubricTraits.PRONUNCIATION, pronunciation,
                RubricTraits.ORAL_FLUENCY, fluency), words);
    }

    @Test
    void essayOf50Words_hasFormZero_andScoresZeroByTheFormGate() {
        EffectiveRubric essay = effective(ESSAY);
        int form = essay.effectiveFormRule().bandFor(metrics(50));
        assertThat(form).isZero();

        ScoreTrace trace = RubricScorer.score(essay, set(essayScores(form, 6)));

        assertThat(trace.itemScore()).isZero();
        assertThat(trace.rawPercent()).isEqualByComparingTo("0");
        assertThat(trace.gateTriggeredBy()).isEqualTo(RubricTraits.FORM);
    }

    @Test
    void essayOf150Words_hasFormOne_andScores62() {
        EffectiveRubric essay = effective(ESSAY);
        int form = essay.effectiveFormRule().bandFor(metrics(150));
        assertThat(form).isEqualTo(1);

        ScoreTrace trace = RubricScorer.score(essay, set(essayScores(form, 4)));

        assertThat(trace.itemScore()).isEqualTo(62);
        assertThat(trace.gateTriggeredBy()).isNull();
        // 16 / 26 = 61.538...: the trace keeps the unrounded value, only the item score is rounded.
        assertThat(trace.rawPercent()).isBetween(new BigDecimal("61.53"), new BigDecimal("61.54"));
        assertThat(trace.contributions()).hasSize(7);
    }

    @Test
    void describeImageWithContentZero_scoresZeroByTheContentGate() {
        ScoreTrace trace = RubricScorer.score(effective(DI), set(diScores(0, 5, 5)));

        assertThat(trace.itemScore()).isZero();
        assertThat(trace.rawPercent()).isEqualByComparingTo("0");
        assertThat(trace.gateTriggeredBy()).isEqualTo(RubricTraits.CONTENT);
    }

    @Test
    void describeImage_4_3_4_scores69() {
        ScoreTrace trace = RubricScorer.score(effective(DI), set(diScores(4, 3, 4)));

        assertThat(trace.itemScore()).isEqualTo(69);
        assertThat(trace.gateTriggeredBy()).isNull();
        assertThat(trace.rawPercent()).isEqualByComparingTo("68.75");
    }

    @Test
    void halfIsRoundedUp() {
        // 2 / 16 = 12.5 exactly.
        ScoreTrace trace = RubricScorer.score(effective(DI), set(diScores(2, 0, 0)));

        assertThat(trace.rawPercent()).isEqualByComparingTo("12.5");
        assertThat(trace.itemScore()).isEqualTo(13);
    }

    @Test
    void summarizeSpokenTextOf45Words_hasFormOne() {
        EffectiveRubric sst = effective(SST);
        int form = sst.effectiveFormRule().bandFor(metrics(45));
        assertThat(form).isEqualTo(1);

        ScoreTrace trace = RubricScorer.score(sst, set(sstScores(3, form)));

        assertThat(trace.itemScore()).isEqualTo(83); // 10 / 12 = 83.33
        assertThat(trace.gateTriggeredBy()).isNull();
    }

    @Test
    void summarizeSpokenTextOf30Words_hasFormZero_andScoresZero() {
        EffectiveRubric sst = effective(SST);
        int form = sst.effectiveFormRule().bandFor(metrics(30));
        assertThat(form).isZero();

        ScoreTrace trace = RubricScorer.score(sst, set(sstScores(4, form)));

        assertThat(trace.itemScore()).isZero();
        assertThat(trace.gateTriggeredBy()).isEqualTo(RubricTraits.FORM);
    }

    @Test
    void summarizeWrittenTextWithTwoSentencesOrAllCaps_hasFormZero_andScoresZero() {
        EffectiveRubric swt = effective(SWT);
        TextMetrics twoSentences = new TextMetrics(40, 2, false, true, false);
        TextMetrics allCaps = new TextMetrics(40, 1, true, true, false);
        for (TextMetrics bad : new TextMetrics[] {twoSentences, allCaps}) {
            int form = swt.effectiveFormRule().bandFor(bad);
            assertThat(form).isZero();

            ScoreTrace trace = RubricScorer.score(swt, set(swtScores(form)));

            assertThat(trace.itemScore()).isZero();
            assertThat(trace.gateTriggeredBy()).isEqualTo(RubricTraits.FORM);
        }
    }

    @Test
    void summarizeWrittenTextInOneValidSentence_scoresFull() {
        EffectiveRubric swt = effective(SWT);
        int form = swt.effectiveFormRule().bandFor(new TextMetrics(30, 1, false, true, false));
        assertThat(form).isEqualTo(1);

        assertThat(RubricScorer.score(swt, set(swtScores(form))).itemScore()).isEqualTo(100);
    }

    @Test
    void bothGatesZero_reportsTheFirstGateInTraitDeclarationOrder() {
        ScoreTrace trace = RubricScorer.score(effective(ESSAY), set(essayScores(0, 0)));

        assertThat(trace.itemScore()).isZero();
        assertThat(trace.gateTriggeredBy()).isEqualTo(RubricTraits.CONTENT);
    }

    @ParameterizedTest
    @CsvSource({
            "1,0,5,5",
            "1,1,3,2",
            "30,3,4,3",
            "30,0,5,5",
            "30,30,0,0",
            "80,8,4,3",
            "80,40,2,5"})
    void readAloud_followsTheContentMinusErrorsFormula(int words, int errors, int pronunciation, int fluency) {
        int content = Math.max(0, words - errors);

        ScoreTrace trace = RubricScorer.score(effective(RA), readAloudScores(words, content, pronunciation, fluency));

        int expected = BigDecimal.valueOf(100L * (content + pronunciation + fluency))
                .divide(BigDecimal.valueOf(words + 10L), 10, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.HALF_UP).intValueExact();
        assertThat(trace.itemScore()).isEqualTo(expected);
        assertThat(trace.gateTriggeredBy()).isNull();
    }

    @Test
    void readAloud_knownValues() {
        assertThat(readAloud(30, 27, 4, 3)).isEqualTo(85); // 34 / 40
        assertThat(readAloud(80, 72, 4, 3)).isEqualTo(88); // 79 / 90 = 87.78
        assertThat(readAloud(1, 0, 5, 5)).isEqualTo(91);   // 10 / 11 = 90.9
        assertThat(readAloud(1, 1, 5, 5)).isEqualTo(100);
    }

    @Test
    void readAloudContributionCarriesTheWordCountAsMaxAndWeight() {
        ScoreTrace trace = RubricScorer.score(effective(RA), readAloudScores(30, 20, 5, 5));

        TraitContribution content = trace.contributions().stream()
                .filter(c -> RubricTraits.CONTENT.equals(c.key())).findFirst().orElseThrow();
        assertThat(content.max()).isEqualTo(30);
        assertThat(content.achieved()).isEqualTo(20);
        assertThat(content.effectiveWeight()).isEqualByComparingTo("30");
    }

    @Test
    void readAloudWithoutAValidWordCount_isRejected() {
        for (Integer bad : new Integer[] {null, 0, -3}) {
            TraitScoreSet input = new TraitScoreSet(scores(RubricTraits.CONTENT, 0, RubricTraits.PRONUNCIATION, 5,
                    RubricTraits.ORAL_FLUENCY, 5), bad);
            assertRubricError(() -> RubricScorer.score(effective(RA), input),
                    RubricConstants.RUBRIC_WORD_COUNT_INVALID);
        }
    }

    @Test
    void readAloudContentAboveTheWordCount_isOutOfRange() {
        assertRubricError(() -> RubricScorer.score(effective(RA), readAloudScores(30, 31, 5, 5)),
                RubricConstants.RUBRIC_TRAIT_OUT_OF_RANGE);
    }

    @Test
    void traitScaleMaxFor() {
        assertThat(TraitScale.fixed(5).maxFor(null)).isEqualTo(5);
        assertThat(TraitScale.fixed(5).isPerWord()).isFalse();
        assertThat(TraitScale.perWord().isPerWord()).isTrue();
        assertThat(TraitScale.perWord().maxFor(30)).isEqualTo(30);
        assertRubricError(() -> TraitScale.perWord().maxFor(null), RubricConstants.RUBRIC_WORD_COUNT_INVALID);
        assertRubricError(() -> TraitScale.perWord().maxFor(0), RubricConstants.RUBRIC_WORD_COUNT_INVALID);
    }

    @Test
    void valueAboveTheScale_isRejectedNotClamped() {
        assertRubricError(() -> RubricScorer.score(effective(DI), set(diScores(7, 5, 5))),
                RubricConstants.RUBRIC_TRAIT_OUT_OF_RANGE);
        assertRubricError(() -> RubricScorer.score(effective(DI), set(diScores(4, 6, 5))),
                RubricConstants.RUBRIC_TRAIT_OUT_OF_RANGE);
        assertRubricError(() -> RubricScorer.score(effective(ESSAY), set(essayScores(3, 4))),
                RubricConstants.RUBRIC_TRAIT_OUT_OF_RANGE);
    }

    @Test
    void negativeValue_isRejectedNotClamped() {
        assertRubricError(() -> RubricScorer.score(effective(DI), set(diScores(-1, 5, 5))),
                RubricConstants.RUBRIC_TRAIT_OUT_OF_RANGE);
    }

    @Test
    void missingTrait_isRejected() {
        assertRubricError(() -> RubricScorer.score(effective(DI), set(scores(RubricTraits.CONTENT, 4,
                RubricTraits.PRONUNCIATION, 3))), RubricConstants.RUBRIC_TRAIT_MISSING);
    }

    @Test
    void unknownTrait_isRejected() {
        assertRubricError(() -> RubricScorer.score(effective(DI), set(scores(RubricTraits.CONTENT, 4,
                RubricTraits.PRONUNCIATION, 3, RubricTraits.ORAL_FLUENCY, 4, "BOGUS", 1))),
                RubricConstants.RUBRIC_TRAIT_UNKNOWN);
        // A trait that exists in the vocabulary but not on this task type is unknown here too.
        assertRubricError(() -> RubricScorer.score(effective(DI), set(scores(RubricTraits.CONTENT, 4,
                RubricTraits.PRONUNCIATION, 3, RubricTraits.ORAL_FLUENCY, 4, RubricTraits.SPELLING, 1))),
                RubricConstants.RUBRIC_TRAIT_UNKNOWN);
    }

    @Test
    void disabledGates_doNotTrigger_andScoreIsSumOverSum() {
        ScoreTrace trace = RubricScorer.score(withGates(DI, false), set(diScores(0, 5, 5)));

        assertThat(trace.gateTriggeredBy()).isNull();
        assertThat(trace.rawPercent()).isEqualByComparingTo("62.5"); // 10 / 16
        assertThat(trace.itemScore()).isEqualTo(63);
    }

    @Test
    void disabledFormGate_letsABadEssayFormScoreThrough() {
        ScoreTrace trace = RubricScorer.score(withGates(ESSAY, false), set(essayScores(0, 4)));

        assertThat(trace.gateTriggeredBy()).isNull();
        assertThat(trace.itemScore()).isEqualTo(58); // 15 / 26 = 57.69
    }

    @Test
    void defaultWeights_equalSumOfAchievedOverSumOfMaximum() {
        ScoreTrace trace = RubricScorer.score(effective(DI), set(diScores(3, 2, 5)));

        assertThat(trace.rawPercent()).isEqualByComparingTo("62.5"); // 10 / 16
    }

    @Test
    void customWeights_changeTheMix() {
        EffectiveRubric equal = withWeights(DI, Map.of(RubricTraits.CONTENT, 1, RubricTraits.PRONUNCIATION, 1,
                RubricTraits.ORAL_FLUENCY, 1));

        ScoreTrace trace = RubricScorer.score(equal, set(diScores(3, 5, 0)));

        assertThat(trace.rawPercent()).isEqualByComparingTo("50"); // (0.5 + 1 + 0) / 3
        assertThat(trace.itemScore()).isEqualTo(50);
    }

    @Test
    void zeroWeightOnANonGateTrait_dropsItFromTheScore() {
        EffectiveRubric noPronunciation = withWeights(DI, Map.of(RubricTraits.PRONUNCIATION, 0));

        ScoreTrace trace = RubricScorer.score(noPronunciation, set(diScores(3, 0, 5)));

        assertThat(trace.rawPercent().setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo("72.73"); // 8 / 11
        assertThat(trace.itemScore()).isEqualTo(73);
    }

    @Test
    void weightBoundaries_0And10AreAccepted() {
        assertThat(withWeights(DI, Map.of(RubricTraits.CONTENT, 10, RubricTraits.PRONUNCIATION, 0))).isNotNull();
    }

    @Test
    void weightOutsideZeroToTen_isRejected() {
        assertRubricError(() -> withWeights(DI, Map.of(RubricTraits.PRONUNCIATION, 11)),
                RubricConstants.RUBRIC_WEIGHT_INVALID);
        assertRubricError(() -> withWeights(DI, Map.of(RubricTraits.PRONUNCIATION, -1)),
                RubricConstants.RUBRIC_WEIGHT_INVALID);
    }

    @Test
    void enabledGateTraitWithWeightZero_isRejected() {
        assertRubricError(() -> withWeights(DI, Map.of(RubricTraits.CONTENT, 0)),
                RubricConstants.RUBRIC_WEIGHT_INVALID);
    }

    @Test
    void totalWeightZero_isRejected() {
        assertRubricError(() -> withWeights("ANSWER_SHORT_QUESTION", Map.of(RubricTraits.VOCABULARY, 0)),
                RubricConstants.RUBRIC_WEIGHT_INVALID);
    }

    @Test
    void missingWeightForATrait_isRejected() {
        assertRubricError(() -> withoutWeight(DI, RubricTraits.ORAL_FLUENCY), RubricConstants.RUBRIC_WEIGHT_INVALID);
    }

    @Test
    void objectiveTaskType_isNotTraitScored() {
        // Built inside the call: an objective rubric has no traits, so constructing it must not fail on weights.
        assertRubricError(() -> RubricScorer.score(effective("MC_READING_SINGLE"), set(Map.of())),
                RubricConstants.RUBRIC_TASK_TYPE_NOT_TRAIT_SCORED);
    }

    private static int readAloud(int words, int content, int pronunciation, int fluency) {
        return RubricScorer.score(effective(RA), readAloudScores(words, content, pronunciation, fluency))
                .itemScore();
    }
}
