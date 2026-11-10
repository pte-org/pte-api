package com.pte.scoring.rubric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.pte.scoring.rubric.RubricTestSupport.task;
import static org.assertj.core.api.Assertions.assertThat;

/** Every boundary of the default Form bands and every mechanical sub-rule. */
class FormRuleBandTest {

    private static final FormRule ESSAY = task("WRITE_ESSAY").formRule();
    private static final FormRule SST = task("SUMMARIZE_SPOKEN_TEXT").formRule();
    private static final FormRule SWT = task("SUMMARIZE_WRITTEN_TEXT").formRule();

    private static TextMetrics prose(int words, int sentences) {
        return new TextMetrics(words, sentences, false, true, false);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "50,0", "119,0", "120,1", "150,1", "199,1", "200,2", "250,2", "300,2", "301,1", "380,1",
            "381,0", "1000,0"})
    void essayBands(int words, int band) {
        assertThat(ESSAY.bandFor(prose(words, 12))).isEqualTo(band);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "39,0", "40,1", "45,1", "49,1", "50,2", "60,2", "70,2", "71,1", "100,1", "101,0"})
    void summarizeSpokenTextBands(int words, int band) {
        assertThat(SST.bandFor(prose(words, 4))).isEqualTo(band);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "4,0", "5,1", "30,1", "75,1", "76,0"})
    void summarizeWrittenTextBands(int words, int band) {
        assertThat(SWT.bandFor(prose(words, 1))).isEqualTo(band);
    }

    @Test
    void summarizeWrittenTextNeedsExactlyOneSentence() {
        assertThat(SWT.requireSingleSentence()).isTrue();
        assertThat(SWT.bandFor(prose(30, 0))).isZero();
        assertThat(SWT.bandFor(prose(30, 1))).isEqualTo(1);
        assertThat(SWT.bandFor(prose(30, 2))).isZero();
    }

    @Test
    void essayAndSpokenSummaryDoNotLookAtTheSentenceCount() {
        assertThat(ESSAY.requireSingleSentence()).isFalse();
        assertThat(SST.requireSingleSentence()).isFalse();
        assertThat(ESSAY.bandFor(prose(250, 1))).isEqualTo(2);
        assertThat(SST.bandFor(prose(60, 9))).isEqualTo(2);
    }

    @Test
    void mechanicalRulesApplyToAllThreeWritingTasks() {
        for (FormRule rule : List.of(ESSAY, SST, SWT)) {
            assertThat(rule.applyMechanicalRules()).isTrue();
        }
    }

    @Test
    void allCapsGivesBandZero() {
        assertThat(ESSAY.bandFor(new TextMetrics(250, 12, true, true, false))).isZero();
        assertThat(SST.bandFor(new TextMetrics(60, 4, true, true, false))).isZero();
        assertThat(SWT.bandFor(new TextMetrics(30, 1, true, true, false))).isZero();
    }

    @Test
    void missingPunctuationGivesBandZero() {
        assertThat(ESSAY.bandFor(new TextMetrics(250, 1, false, false, false))).isZero();
        assertThat(SST.bandFor(new TextMetrics(60, 1, false, false, false))).isZero();
        assertThat(SWT.bandFor(new TextMetrics(30, 1, false, false, false))).isZero();
    }

    @Test
    void bulletOnlyGivesBandZero() {
        assertThat(ESSAY.bandFor(new TextMetrics(250, 12, false, true, true))).isZero();
        assertThat(SST.bandFor(new TextMetrics(60, 4, false, true, true))).isZero();
        assertThat(SWT.bandFor(new TextMetrics(30, 1, false, true, true))).isZero();
    }

    @Test
    void mechanicalRulesCanBeSwitchedOffOnARuleInstance() {
        FormRule lenient = new FormRule(ESSAY.bands(), false, false);

        assertThat(lenient.bandFor(new TextMetrics(250, 12, true, false, true))).isEqualTo(2);
    }

    @Test
    void maxBandIsTheHighestBand() {
        assertThat(ESSAY.maxBand()).isEqualTo(2);
        assertThat(SST.maxBand()).isEqualTo(2);
        assertThat(SWT.maxBand()).isEqualTo(1);
    }

    @Test
    void defaultBandRangesAreTheDocumentedOnes() {
        assertThat(ESSAY.bands()).containsExactlyInAnyOrder(
                new FormBand(200, 300, 2), new FormBand(120, 199, 1), new FormBand(301, 380, 1));
        assertThat(SST.bands()).containsExactlyInAnyOrder(
                new FormBand(50, 70, 2), new FormBand(40, 49, 1), new FormBand(71, 100, 1));
        assertThat(SWT.bands()).containsExactly(new FormBand(5, 75, 1));
    }

    @Test
    void withBandsReplacesTheBandsAndKeepsTheOtherRules() {
        FormRule custom = SWT.withBands(List.of(new FormBand(10, 50, 1)));

        assertThat(custom.bands()).containsExactly(new FormBand(10, 50, 1));
        assertThat(custom.requireSingleSentence()).isTrue();
        assertThat(custom.applyMechanicalRules()).isTrue();
        assertThat(custom.bandFor(prose(9, 1))).isZero();
        assertThat(custom.bandFor(prose(10, 1))).isEqualTo(1);
        assertThat(custom.bandFor(prose(50, 1))).isEqualTo(1);
        assertThat(custom.bandFor(prose(51, 1))).isZero();
        assertThat(custom.bandFor(prose(30, 2))).isZero();
    }

    @Test
    void withBandsOverridesEssayRangesWithoutTouchingTheOriginal() {
        FormRule custom = ESSAY.withBands(List.of(new FormBand(100, 200, 2), new FormBand(50, 99, 1)));

        assertThat(custom.bandFor(prose(150, 5))).isEqualTo(2);
        assertThat(custom.bandFor(prose(200, 5))).isEqualTo(2);
        assertThat(custom.bandFor(prose(201, 5))).isZero();
        assertThat(custom.bandFor(prose(75, 5))).isEqualTo(1);
        assertThat(custom.bandFor(prose(49, 5))).isZero();
        assertThat(custom.maxBand()).isEqualTo(2);
        // The catalog rule is immutable.
        assertThat(ESSAY.bandFor(prose(150, 5))).isEqualTo(1);
        assertThat(ESSAY.bandFor(prose(250, 5))).isEqualTo(2);
    }

    @Test
    void effectiveFormRuleUsesTheParameterBandsWhenPresent() {
        RubricParameters defaults = RubricTestSupport.CATALOG.defaultParameters("WRITE_ESSAY");
        List<FormBand> custom = List.of(new FormBand(100, 200, 2));
        EffectiveRubric rubric = new EffectiveRubric(task("WRITE_ESSAY"),
                new RubricParameters(defaults.traitWeights(), custom, defaults.gateEnabled(), null));

        assertThat(rubric.effectiveFormRule().bands()).isEqualTo(custom);
        assertThat(rubric.effectiveFormRule().bandFor(prose(150, 5))).isEqualTo(2);
        assertThat(RubricTestSupport.effective("WRITE_ESSAY").effectiveFormRule().bands())
                .containsExactlyInAnyOrderElementsOf(ESSAY.bands());
    }
}
