package com.pte.reporting.internal.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.internal.service.aggregation.AggregationTrace;
import com.pte.reporting.internal.service.aggregation.SkillTermStep;
import com.pte.reporting.internal.service.aggregation.SkillTrace;
import com.pte.reporting.internal.service.aggregation.TaskTypeAverageStep;
import com.pte.reporting.internal.service.explain.ExplanationFixtureBuilder;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The aggregation trace (FR-07): every aggregate method reads its numbers off the same trace, blanks count as 0, a
 * weighted task type with nothing scored is excluded from Σw, Σw = 0 is insufficient data, and Overall is null
 * unless all four skills were tested.
 */
class ScoreAggregationServiceTraceTest {

    private static final UUID ATTEMPT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    private final ScoringService scoringService = mock(ScoringService.class);
    private final AttemptService attemptService = mock(AttemptService.class);
    private final ExamDefinitionService definitions = mock(ExamDefinitionService.class);
    private final ScoreAggregationService service =
            new ScoreAggregationService(scoringService, attemptService, definitions);

    @Test
    void everyAggregateMethodReadsTheSameNumbersOffTheTraceOnTheFullFixture() {
        ExplanationFixtureBuilder.Fixture fixture = ExplanationFixtureBuilder.allCases().build();
        ScoreAggregationService aggregation = fixture.aggregationService(scoringService);
        UUID attempt = ExplanationFixtureBuilder.ATTEMPT_ID;
        when(scoringService.getReportScoringInputsForAttempt(TENANT_ID, attempt)).thenReturn(fixture.answers());

        TracedAggregation traced = aggregation.aggregateWithTrace(attempt, fixture.answers());
        ReportScoreAggregation batch = aggregation.aggregateBatchFromInputs(Set.of(attempt),
                Map.of(attempt, fixture.answers())).get(attempt);

        assertThat(traced.summary()).isEqualTo(ScoreAggregationService.toSummary(traced.trace()));
        assertThat(aggregation.aggregate(attempt, TENANT_ID)).isEqualTo(traced.summary());
        assertThat(aggregation.aggregateFromInputs(attempt, TENANT_ID, fixture.answers()))
                .isEqualTo(traced.summary());
        assertThat(aggregation.aggregateProvisional(attempt, TENANT_ID).summary()).isEqualTo(traced.summary());
        assertThat(batch.summary()).isEqualTo(traced.summary());
        assertThat(batch.trace()).isEqualTo(traced.trace());
        assertThat(batch.rubricVersionPublicId()).isEqualTo(fixture.pinnedVersion().getPublicId());
        assertThat(traced.trace().skills()).containsOnlyKeys(Skill.values());
        assertThat(traced.trace().overall()).isNotNull();
        assertThat(traced.trace().overall().score()).isBetween(10, 90);
    }

    @Test
    void aBlankItemCountsAsZeroInItsTaskTypeAverage() {
        stub(Set.of("WRITING"), Map.of("WRITE_ESSAY", 3), item("WRITE_ESSAY", null, bd(1), null));
        AggregationTrace trace = service.aggregateWithTrace(ATTEMPT_ID,
                List.of(answer("WRITE_ESSAY", 80), answer("WRITE_ESSAY", 70))).trace();

        TaskTypeAverageStep step = trace.taskTypeAverages().getFirst();
        assertThat(step.scoredCount()).isEqualTo(2);
        assertThat(step.blankCount()).isEqualTo(1);
        assertThat(step.sum()).isEqualByComparingTo("150");
        assertThat(step.average()).isEqualByComparingTo("50");
        SkillTrace writing = trace.skills().get(Skill.WRITING);
        assertThat(writing.score()).isEqualTo(50);
        assertThat(writing.scaledScore()).isEqualByComparingTo("50");
    }

    @Test
    void aWeightedTaskTypeWithNothingScoredIsExcludedFromBothSums() {
        stub(Set.of("WRITING"), Map.of(), item("WRITE_ESSAY", null, bd(2), null),
                item("SUMMARIZE_WRITTEN_TEXT", null, bd(1), null));
        SkillTrace writing = service.aggregateWithTrace(ATTEMPT_ID, List.of(answer("WRITE_ESSAY", 75)))
                .trace().skills().get(Skill.WRITING);

        assertThat(writing.terms()).extracting(SkillTermStep::taskType, SkillTermStep::excluded)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("WRITE_ESSAY", false),
                        org.assertj.core.groups.Tuple.tuple("SUMMARIZE_WRITTEN_TEXT", true));
        SkillTermStep excluded = writing.terms().get(1);
        assertThat(excluded.average()).isNull();
        assertThat(excluded.contribution()).isNull();
        assertThat(writing.totalWeight()).isEqualByComparingTo("2");
        assertThat(writing.weightedSum()).isEqualByComparingTo("1.5");
        assertThat(writing.ratio()).isEqualByComparingTo("0.75");
        assertThat(writing.scaledScore()).isEqualByComparingTo("70");
        assertThat(writing.score()).isEqualTo(70);
    }

    @Test
    void zeroTotalWeightIsInsufficientDataWithoutRatioOrScore() {
        stub(Set.of("READING"), Map.of(), item("MC_READING_SINGLE", null, null, bd(1)));
        SkillTrace reading = service.aggregateWithTrace(ATTEMPT_ID, List.of()).trace().skills().get(Skill.READING);

        assertThat(reading.sufficientData()).isFalse();
        assertThat(reading.totalWeight()).isEqualByComparingTo("0");
        assertThat(reading.ratio()).isNull();
        assertThat(reading.scaledScore()).isNull();
        assertThat(reading.score()).isNull();
        assertThat(service.aggregateFromInputs(ATTEMPT_ID, TENANT_ID, List.of()).skillScores().get(Skill.READING))
                .isEqualTo(SkillScore.insufficientData(BigDecimal.ZERO));
    }

    @Test
    void overallIsNullUnlessAllFourSkillsAreTestedAndHalfUpRoundingIsTraced() {
        stub(Set.of("WRITING"), Map.of(), item("WRITE_ESSAY", bd(1), bd(1), null));
        // Average 445 / 8 = 55.625, so 10 + 80 × 0.55625 = 54.5 exactly: rounds half-up to 55.
        List<ReportScoringAnswerView> answers = new java.util.ArrayList<>();
        for (int score : new int[] {55, 55, 55, 56, 56, 56, 56, 56}) {
            answers.add(answer("WRITE_ESSAY", score));
        }
        AggregationTrace trace = service.aggregateWithTrace(ATTEMPT_ID, answers).trace();

        assertThat(trace.overall()).isNull();
        SkillTrace writing = trace.skills().get(Skill.WRITING);
        assertThat(writing.scaledScore()).isEqualByComparingTo("54.5");
        assertThat(writing.score()).isEqualTo(55);
    }

    private void stub(Set<String> sections, Map<String, Integer> expected, ExamDefinitionItemResponse... items) {
        when(attemptService.getScoreContext(ATTEMPT_ID))
                .thenReturn(new AttemptScoreContextView(TEMPLATE_ID, 1, sections, expected));
        when(definitions.getByPublicId(TEMPLATE_ID)).thenReturn(new ExamDefinitionResponse(TEMPLATE_ID, "T", 1, "T",
                "ACTIVE", List.of(items)));
    }

    private static ReportScoringAnswerView answer(String taskType, int score) {
        return new ReportScoringAnswerView(UUID.randomUUID(), ATTEMPT_ID, TEMPLATE_ID, taskType, "WRITING", "AI_TEXT",
                score, "REAL", "p", "m", "v", null, "AI", score, true, null, 0);
    }

    private static ExamDefinitionItemResponse item(String taskType, BigDecimal overall, BigDecimal writing,
            BigDecimal reading) {
        return new ExamDefinitionItemResponse(taskType, "WRITING", 0, 1, 1, 0, 0, "AI_TEXT", overall, BigDecimal.ZERO,
                writing, reading, BigDecimal.ZERO);
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
