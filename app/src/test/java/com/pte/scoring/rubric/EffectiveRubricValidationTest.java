package com.pte.scoring.rubric;

import com.pte.scoring.internal.constant.RubricConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.pte.scoring.rubric.RubricTestSupport.CATALOG;
import static com.pte.scoring.rubric.RubricTestSupport.assertRubricError;
import static com.pte.scoring.rubric.RubricTestSupport.metrics;
import static com.pte.scoring.rubric.RubricTestSupport.scores;
import static com.pte.scoring.rubric.RubricTestSupport.set;
import static com.pte.scoring.rubric.RubricTestSupport.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Construction-time validation of form bands and gate keys, plus a valid custom band list end to end. */
class EffectiveRubricValidationTest {

    private static final String ESSAY = "WRITE_ESSAY";
    private static final String SWT = "SUMMARIZE_WRITTEN_TEXT";
    private static final String SST = "SUMMARIZE_SPOKEN_TEXT";
    private static final String DI = "DESCRIBE_IMAGE";
    private static final String RA = "READ_ALOUD";
    private static final String OBJECTIVE = "MC_READING_SINGLE";

    private static EffectiveRubric withBands(String taskType, List<FormBand> bands) {
        RubricParameters defaults = CATALOG.defaultParameters(taskType);
        return new EffectiveRubric(task(taskType), new RubricParameters(defaults.traitWeights(), bands,
                defaults.gateEnabled(), defaults.azureMappingVersion()));
    }

    private static EffectiveRubric withGateMap(String taskType, Map<String, Boolean> gates) {
        RubricParameters defaults = CATALOG.defaultParameters(taskType);
        return new EffectiveRubric(task(taskType), new RubricParameters(defaults.traitWeights(),
                defaults.formBands(), gates, defaults.azureMappingVersion()));
    }

    private static void assertBandsInvalid(String taskType, List<FormBand> bands) {
        assertRubricError(() -> withBands(taskType, bands), RubricConstants.RUBRIC_FORM_BANDS_INVALID);
    }

    @Test
    void defaultParametersOfEveryFormTaskAreValid() {
        for (String type : List.of(ESSAY, SWT, SST, DI, RA, OBJECTIVE)) {
            assertThatCode(() -> new EffectiveRubric(task(type), CATALOG.defaultParameters(type)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void nullBandsForATaskWithAFormRule_areRejected() {
        for (String type : List.of(ESSAY, SWT, SST)) {
            assertBandsInvalid(type, null);
        }
    }

    @Test
    void bandsForATaskWithoutAFormRule_areRejected() {
        List<FormBand> bands = List.of(new FormBand(0, 100, 1));
        assertBandsInvalid(DI, bands);
        assertBandsInvalid(OBJECTIVE, bands);
    }

    @Test
    void emptyBandList_isRejected() {
        assertBandsInvalid(ESSAY, List.of());
    }

    @Test
    void allBandsZero_isRejected() {
        assertBandsInvalid(ESSAY, List.of(new FormBand(0, 99, 0), new FormBand(100, 300, 0)));
    }

    @Test
    void negativeBand_isRejected() {
        assertBandsInvalid(ESSAY, List.of(new FormBand(100, 200, 2), new FormBand(0, 99, -1)));
    }

    @Test
    void negativeMinWords_isRejected() {
        assertBandsInvalid(ESSAY, List.of(new FormBand(-1, 200, 2)));
    }

    @Test
    void minWordsAboveMaxWords_isRejected() {
        assertBandsInvalid(ESSAY, List.of(new FormBand(200, 100, 2)));
    }

    @Test
    void overlappingRanges_areRejected() {
        assertBandsInvalid(ESSAY, List.of(new FormBand(100, 250, 2), new FormBand(200, 300, 1)));
    }

    @Test
    void touchingRanges_areRejectedAsOverlapping() {
        assertBandsInvalid(ESSAY, List.of(new FormBand(100, 200, 2), new FormBand(200, 300, 1)));
    }

    @Test
    void overlapIsDetectedRegardlessOfListOrder() {
        assertBandsInvalid(ESSAY, List.of(new FormBand(200, 300, 1), new FormBand(100, 200, 2)));
    }

    @Test
    void adjacentNonOverlappingRanges_areAccepted() {
        assertThatCode(() -> withBands(ESSAY, List.of(new FormBand(200, 300, 1), new FormBand(100, 199, 2))))
                .doesNotThrowAnyException();
    }

    @Test
    void gateKeyThatIsNotAGateTraitOfTheTask_isRejected() {
        assertRubricError(() -> withGateMap(DI, Map.of(RubricTraits.PRONUNCIATION, true)),
                RubricConstants.RUBRIC_WEIGHT_INVALID);
        assertRubricError(() -> withGateMap(DI, Map.of("BOGUS", false)), RubricConstants.RUBRIC_WEIGHT_INVALID);
    }

    @Test
    void anyGateKeyOnATaskWithoutGates_isRejected() {
        assertThat(task(RA).gateTraits()).isEmpty();
        assertRubricError(() -> withGateMap(RA, Map.of(RubricTraits.CONTENT, true)),
                RubricConstants.RUBRIC_WEIGHT_INVALID);
    }

    @Test
    void gateKeyThatIsAGateTrait_isAccepted() {
        assertThatCode(() -> withGateMap(DI, Map.of(RubricTraits.CONTENT, false))).doesNotThrowAnyException();
    }

    @Test
    void customBandList_worksEndToEndForAnEssay() {
        EffectiveRubric essay = withBands(ESSAY, List.of(new FormBand(100, 200, 2)));

        int form = essay.effectiveFormRule().bandFor(metrics(150));
        assertThat(form).isEqualTo(2);
        assertThat(essay.effectiveFormRule().maxBand()).isEqualTo(2);
        assertThat(essay.effectiveFormRule().bandFor(metrics(99))).isZero();
        assertThat(essay.effectiveFormRule().bandFor(metrics(201))).isZero();

        ScoreTrace trace = RubricScorer.score(essay, set(scores(RubricTraits.CONTENT, 4,
                RubricTraits.FORM, form, RubricTraits.DEVELOPMENT_STRUCTURE_COHERENCE, 4,
                RubricTraits.GRAMMAR, 1, RubricTraits.GENERAL_LINGUISTIC_RANGE, 4,
                RubricTraits.VOCABULARY_RANGE, 1, RubricTraits.SPELLING, 1)));

        TraitContribution formContribution = trace.contributions().stream()
                .filter(c -> RubricTraits.FORM.equals(c.key())).findFirst().orElseThrow();
        assertThat(formContribution.max()).isEqualTo(2);
        assertThat(formContribution.achieved()).isEqualTo(2);
        assertThat(trace.gateTriggeredBy()).isNull();
        assertThat(trace.itemScore()).isEqualTo(65); // 17 / 26 = 65.38
    }
}
