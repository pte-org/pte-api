package com.pte.scoring.internal.rubric.stability;

import com.pte.scoring.internal.rubric.stability.StabilityMetrics.RunOutcome;
import com.pte.scoring.internal.rubric.stability.StabilityMetrics.SampleRuns;
import com.pte.scoring.internal.rubric.stability.StabilityMetrics.StabilityReport;
import com.pte.scoring.internal.rubric.stability.StabilityMetrics.TraitStat;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StabilityMetricsTest {

    private static final String WE = "WRITE_ESSAY";
    private static final String SWT = "SUMMARIZE_WRITTEN_TEXT";
    private static final List<String> TRAITS = List.of("CONTENT", "GRAMMAR");

    @Test
    void identicalRunsAreFullyStable() {
        StabilityReport report = StabilityMetrics.compute(List.of(sample("a", WE, Map.of("CONTENT", 4, "GRAMMAR", 2),
                ok(4, 2), ok(4, 2), ok(4, 2))));

        assertThat(report.overall().total()).isEqualTo(2);
        assertThat(report.overall().stable()).isEqualTo(2);
        assertThat(report.overall().stableRate()).isEqualTo(1.0);
        assertThat(report.meetsGate()).isTrue();
    }

    @Test
    void aSpreadOfOneIsStableAndASpreadOfTwoIsNot() {
        StabilityReport report = StabilityMetrics.compute(List.of(
                sample("a", WE, Map.of(), ok(4, 2), ok(5, 1), ok(4, 2)),
                sample("b", WE, Map.of(), ok(3, 2), ok(5, 2), ok(4, 2))));

        TraitStat content = report.byTrait().get(WE + ":CONTENT");
        assertThat(content.total()).isEqualTo(2);
        assertThat(content.stable()).isEqualTo(1);
        assertThat(report.byTrait().get(WE + ":GRAMMAR").stable()).isEqualTo(2);
        assertThat(report.overall().stable()).isEqualTo(3);
        assertThat(report.overall().stableRate()).isEqualTo(0.75);
        assertThat(report.meetsGate()).isFalse();
    }

    @Test
    void aFailedRunMakesEveryTraitOfThatSampleUnstableAndIsCountedWithItsReason() {
        StabilityReport report = StabilityMetrics.compute(List.of(
                sample("a", WE, Map.of(), ok(4, 2), RunOutcome.failure("PROVIDER_RESPONSE_INVALID", 30), ok(4, 2)),
                sample("b", WE, Map.of(), ok(4, 2), ok(4, 2), ok(4, 2))));

        assertThat(report.overall().total()).isEqualTo(4);
        assertThat(report.overall().complete()).isEqualTo(2);
        assertThat(report.overall().stable()).isEqualTo(2);
        assertThat(report.overall().stableRate()).isEqualTo(0.5);
        assertThat(report.overall().stableRateAmongComplete()).isEqualTo(1.0);
        assertThat(report.runs()).isEqualTo(6);
        assertThat(report.failedRuns()).isEqualTo(1);
        assertThat(report.failureRate()).isEqualTo(1.0 / 6);
        assertThat(report.failureReasons()).containsExactly(Map.entry("PROVIDER_RESPONSE_INVALID", 1));
    }

    @Test
    void missingRunsCountAsIncompleteAndTooManyRunsAreRejected() {
        StabilityReport report = StabilityMetrics.compute(List.of(sample("a", WE, Map.of(), ok(4, 2), ok(4, 2))));

        assertThat(report.overall().complete()).isZero();
        assertThat(report.overall().stable()).isZero();
        assertThat(report.failedRuns()).isEqualTo(1);
        assertThat(report.failureReasons()).containsEntry(StabilityMetrics.MISSING_RUN, 1);
        assertThatThrownBy(() -> StabilityMetrics.compute(List.of(
                sample("b", WE, Map.of(), ok(4, 2), ok(4, 2), ok(4, 2), ok(4, 2)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRunWithoutOneTraitMakesOnlyThatTraitIncomplete() {
        RunOutcome noGrammar = RunOutcome.success(Map.of("CONTENT", 4), 1, 10);
        StabilityReport report = StabilityMetrics.compute(List.of(sample("a", WE, Map.of(), ok(4, 2), noGrammar,
                ok(4, 2))));

        assertThat(report.byTrait().get(WE + ":CONTENT").stable()).isEqualTo(1);
        assertThat(report.byTrait().get(WE + ":GRAMMAR").complete()).isZero();
        assertThat(report.failedRuns()).isZero();
    }

    @Test
    void schemaRetriesAreCountedPerRun() {
        StabilityReport report = StabilityMetrics.compute(List.of(sample("a", WE, Map.of(),
                RunOutcome.success(Map.of("CONTENT", 4, "GRAMMAR", 2), 3, 10), ok(4, 2),
                RunOutcome.success(Map.of("CONTENT", 4, "GRAMMAR", 2), 2, 10))));

        assertThat(report.runsWithRetry()).isEqualTo(2);
        assertThat(report.extraAttempts()).isEqualTo(3);
    }

    @Test
    void intendedBandMatchIsReportedPerRunScoreWithinOne() {
        StabilityReport report = StabilityMetrics.compute(List.of(sample("a", SWT, Map.of("CONTENT", 4),
                ok(4, 2), ok(3, 2), ok(2, 2))));

        TraitStat content = report.byTrait().get(SWT + ":CONTENT");
        assertThat(content.intendedChecked()).isEqualTo(3);
        assertThat(content.intendedWithinOne()).isEqualTo(2);
        assertThat(report.byTrait().get(SWT + ":GRAMMAR").intendedChecked()).isZero();
        assertThat(report.overall().intendedWithinOneRate()).isEqualTo(2.0 / 3);
    }

    @Test
    void gateIsInclusiveAtNinetyPercent() {
        List<SampleRuns> samples = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            samples.add(sample("s" + index, WE, Map.of(), ok(4, 2), ok(4, 2), ok(4, 2)));
        }
        samples.add(sample("off", WE, Map.of(), ok(4, 2), ok(4, 0), ok(4, 2)));

        StabilityReport report = StabilityMetrics.compute(samples);

        assertThat(report.overall().stable()).isEqualTo(9);
        assertThat(report.overall().total()).isEqualTo(10);
        assertThat(report.meetsGate()).isTrue();
    }

    @Test
    void latencyIsAveragedAndMaximisedOverEveryRunIncludingFailures() {
        StabilityReport report = StabilityMetrics.compute(List.of(sample("a", WE, Map.of(),
                RunOutcome.success(Map.of("CONTENT", 4, "GRAMMAR", 2), 1, 100),
                RunOutcome.success(Map.of("CONTENT", 4, "GRAMMAR", 2), 1, 200),
                RunOutcome.failure("PROVIDER_TIMEOUT", 600))));

        assertThat(report.latencyAverageMs()).isEqualTo(300);
        assertThat(report.latencyMaxMs()).isEqualTo(600);
    }

    @Test
    void noSamplesNeverMeetTheGate() {
        StabilityReport report = StabilityMetrics.compute(List.of());

        assertThat(report.overall().stableRate()).isZero();
        assertThat(report.failureRate()).isZero();
        assertThat(report.meetsGate()).isFalse();
    }

    @Test
    void aggregatesHoldCountsAndRatesButNeverAnswerTextOrSampleIds() {
        Map<String, Object> aggregates = StabilityMetrics.compute(List.of(sample("secret-sample-id", WE,
                Map.of("CONTENT", 4), ok(4, 2), ok(4, 2), ok(4, 2)))).toAggregates();

        assertThat(aggregates).containsKeys("overall", "byTrait", "runs", "failedRuns", "failureReasons",
                "runsWithRetry", "extraAttempts", "latencyAverageMs", "latencyMaxMs", "gateThreshold", "meetsGate");
        assertThat(aggregates.toString()).doesNotContain("secret-sample-id");
    }

    private static RunOutcome ok(int content, int grammar) {
        Map<String, Integer> traits = new LinkedHashMap<>();
        traits.put("CONTENT", content);
        traits.put("GRAMMAR", grammar);
        return RunOutcome.success(traits, 1, 10);
    }

    private static SampleRuns sample(String id, String taskType, Map<String, Integer> intended, RunOutcome... runs) {
        return new SampleRuns(id, taskType, TRAITS, intended, List.of(runs));
    }
}
