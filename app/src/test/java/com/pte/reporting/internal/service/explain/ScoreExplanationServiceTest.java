package com.pte.reporting.internal.service.explain;

import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.reporting.domain.ExamResult;
import com.pte.reporting.internal.dto.response.ItemExplanationResponse;
import com.pte.reporting.internal.dto.response.ReportResponse;
import com.pte.reporting.internal.dto.response.ScoreExplanationResponse;
import com.pte.reporting.internal.dto.response.SkillExplanationResponse;
import com.pte.reporting.internal.dto.response.SkillScoreResponse;
import com.pte.reporting.internal.mapper.ReportMapper;
import com.pte.reporting.internal.repository.ExamResultRepository;
import com.pte.reporting.internal.service.ProvisionalScoreSummary;
import com.pte.reporting.internal.service.ReportScoreAggregation;
import com.pte.reporting.internal.service.ReportSnapshotCodec;
import com.pte.reporting.internal.service.ScoreAggregationService;
import com.pte.scoring.ScoringService;
import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.dto.response.ItemTraceKind;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.service.RubricVersionFixtures;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.pte.reporting.internal.service.explain.ExplanationFixtureBuilder.ATTEMPT_ID;
import static com.pte.reporting.internal.service.explain.ExplanationFixtureBuilder.SESSION_ID;
import static com.pte.reporting.internal.service.explain.ExplanationFixtureBuilder.TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * S3 / FR-07 on the full fixture (22 task types and every edge case): every number of the explanation matches the
 * published snapshot and the report with zero difference, recomputation calls no AI, changing the ACTIVE rubric does
 * not change a published explanation, a draft's parameters are never shown, and p95 stays under 800 ms.
 */
class ScoreExplanationServiceTest {

    private static final CurrentUser HOST = new CurrentUser(UUID.randomUUID(), TENANT_ID, List.of("HOST_ADMIN"));
    private static final AttemptSummaryView ATTEMPT = new AttemptSummaryView(ATTEMPT_ID, SESSION_ID,
            UUID.randomUUID(), TENANT_ID, UUID.randomUUID());

    private final ScoringService scoringService = mock(ScoringService.class);
    private final ExamResultRepository results = mock(ExamResultRepository.class);
    private final ScoreExplanationAccess access = mock(ScoreExplanationAccess.class);
    private final ReportSnapshotCodec codec = new ReportSnapshotCodec(JsonMapper.builder().build());

    @Test
    void anUnpublishedAttemptIsExplainedLiveAndMatchesTheHostReport() {
        ExplanationFixtureBuilder.Fixture fixture = stubbed(ExplanationFixtureBuilder.allCases()
                .withUnselectedAiAnswer(ExplanationFixtureBuilder.DESCRIBE_IMAGE).build());
        ScoreAggregationService aggregation = fixture.aggregationService(scoringService);
        ScoreExplanationService service = service(fixture, aggregation);

        ScoreExplanationResponse explanation = service.explain(ATTEMPT_ID, HOST);
        ProvisionalScoreSummary live = aggregation.aggregateProvisional(ATTEMPT_ID, TENANT_ID);
        ReportResponse report = ReportMapper.toResponse(new ExamResult(), live.summary(), true);

        assertThat(explanation.published()).isFalse();
        assertThat(explanation.schemaVersion()).isNull();
        assertThat(explanation.traceAvailable()).isTrue();
        assertThat(explanation.includesUnselectedAiScores()).isTrue();
        assertSameScores(explanation, report);
        assertItemTracesMatchItemScores(explanation);
        assertThat(explanation.rubricParameters().versionPublicId()).isEqualTo(fixture.pinnedVersion().getPublicId());
        assertThat(explanation.rubricParameters().taskTypes()).hasSize(10);
    }

    @Test
    void aPublishedAttemptIsReadFromItsSnapshotWithZeroDifferenceAcrossTheWholeFixture() {
        ExplanationFixtureBuilder.Fixture fixture = stubbed(ExplanationFixtureBuilder.allCases().build());
        ScoreAggregationService aggregation = fixture.aggregationService(scoringService);
        ScoreExplanationResponse live = service(fixture, aggregation).explain(ATTEMPT_ID, HOST);
        ExamResult report = publish(fixture, aggregation);

        ScoreExplanationResponse published = service(fixture, aggregation).explain(ATTEMPT_ID, HOST);

        assertThat(published.published()).isTrue();
        assertThat(published.schemaVersion()).isEqualTo(2);
        assertThat(published.traceAvailable()).isTrue();
        assertThat(published.rubricVersionPublicId()).isEqualTo(fixture.pinnedVersion().getPublicId());
        assertThat(published).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFields("published", "publishedAt", "schemaVersion").isEqualTo(live);
        assertSameScores(published, ReportMapper.toResponse(report,
                codec.decode(report.getReportSnapshotJson()).scoreSummary()));
        assertItemTracesMatchItemScores(published);
        assertThat(published.items().stream().map(ItemExplanationResponse::taskType).distinct())
                .hasSize(RubricVersionFixtures.CATALOG.all().size()).hasSize(22);
        assertThat(published.items()).extracting(ItemExplanationResponse::traceKind).contains("RUBRIC", "OBJECTIVE",
                "EXAMINER_SCORE", "LEGACY_NO_TRACE");
        assertThat(published.items()).filteredOn(item -> "READ_ALOUD".equals(item.taskType()))
                .extracting(ItemExplanationResponse::wordCount).contains(1, 30, 80);
        assertThat(published.taskTypeAverages()).filteredOn(step -> "WRITE_FROM_DICTATION".equals(step.taskType()))
                .singleElement().satisfies(step -> assertThat(step.blankCount()).isEqualTo(1));
    }

    @Test
    void changingTheActiveRubricAndTheLiveRowsDoesNotChangeAPublishedExplanation() {
        ExplanationFixtureBuilder.Fixture fixture = stubbed(ExplanationFixtureBuilder.allCases().build());
        ScoreAggregationService aggregation = fixture.aggregationService(scoringService);
        publish(fixture, aggregation);
        ScoreExplanationResponse before = service(fixture, aggregation).explain(ATTEMPT_ID, HOST);

        ReflectionTestUtils.setField(fixture.pinnedVersion(), "status", RubricVersionStatus.RETIRED);
        ScoringRubricVersion newActive = RubricVersionFixtures.version(RubricVersionStatus.ACTIVE,
                RubricVersionFixtures.editTask("DESCRIBE_IMAGE", task -> RubricVersionFixtures.child(task,
                        RubricConstants.PARAM_TRAIT_WEIGHTS).put(RubricTraits.CONTENT, 1)));
        when(fixture.versions().findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(newActive));
        when(scoringService.getReportScoringInputsForAttempt(TENANT_ID, ATTEMPT_ID)).thenReturn(List.of());
        ScoreExplanationResponse after = service(fixture, aggregation).explain(ATTEMPT_ID, HOST);

        assertThat(after).usingRecursiveComparison().ignoringFields("rubricParameters.status").isEqualTo(before);
        assertThat(after.rubricParameters().status()).isEqualTo("RETIRED");
        verify(scoringService, never()).getReportScoringInputsForAttempt(any(), any());
    }

    @Test
    void aDraftPinIsNeverShownAndTheLivePathCallsNothingButTheScoringReadModel() {
        ExplanationFixtureBuilder.Fixture fixture = stubbed(ExplanationFixtureBuilder.allCases().build());
        ReflectionTestUtils.setField(fixture.pinnedVersion(), "status", RubricVersionStatus.DRAFT);

        ScoreExplanationResponse explanation = service(fixture, fixture.aggregationService(scoringService))
                .explain(ATTEMPT_ID, HOST);

        assertThat(explanation.rubricParameters()).isNull();
        verify(scoringService).getReportScoringInputsForAttempt(TENANT_ID, ATTEMPT_ID);
        verifyNoMoreInteractions(scoringService);
    }

    @Test
    void aSchemaOneSnapshotShowsThePublishedScoresWithoutATrace() {
        ExplanationFixtureBuilder.Fixture fixture = stubbed(ExplanationFixtureBuilder.allCases().build());
        ExamResult report = new ExamResult();
        report.publishSnapshot(ExplanationFixtureBuilder.v1SnapshotJson(), UUID.randomUUID(), UUID.randomUUID(), 4,
                Instant.parse("2026-09-24T10:15:30Z"));
        when(results.findByAttemptPublicId(ATTEMPT_ID)).thenReturn(Optional.of(report));

        ScoreExplanationResponse explanation = service(fixture, fixture.aggregationService(scoringService))
                .explain(ATTEMPT_ID, HOST);

        assertThat(explanation.schemaVersion()).isEqualTo(1);
        assertThat(explanation.traceAvailable()).isFalse();
        assertThat(explanation.taskTypeAverages()).isEmpty();
        assertThat(explanation.skills()).extracting(SkillExplanationResponse::skill, SkillExplanationResponse::score)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("READING", null),
                        org.assertj.core.groups.Tuple.tuple("WRITING", 81));
        assertThat(explanation.items()).singleElement().satisfies(item -> {
            assertThat(item.itemScore()).isEqualTo(81);
            assertThat(item.scoreSource()).isEqualTo("EXAMINER");
            assertThat(item.traceKind()).isNull();
        });
        verify(scoringService, never()).getReportScoringInputsForAttempt(any(), any());
    }

    @Test
    void p95StaysUnder800MillisecondsOnTheFixture() {
        ExplanationFixtureBuilder.Fixture fixture = stubbed(ExplanationFixtureBuilder.allCases().build());
        ScoreAggregationService aggregation = fixture.aggregationService(scoringService);
        ScoreExplanationService live = service(fixture, aggregation);
        List<Long> nanos = new ArrayList<>();
        for (int run = 0; run < 100; run++) {
            long start = System.nanoTime();
            live.explain(ATTEMPT_ID, HOST);
            nanos.add(System.nanoTime() - start);
        }
        Collections.sort(nanos);

        assertThat(nanos.get(94) / 1_000_000).isLessThan(800);
    }

    private ScoreExplanationService service(ExplanationFixtureBuilder.Fixture fixture,
            ScoreAggregationService aggregation) {
        when(access.requireReadable(ATTEMPT_ID, HOST)).thenReturn(ATTEMPT);
        return new ScoreExplanationService(access, results, codec, scoringService, aggregation,
                fixture.traceService());
    }

    private ExplanationFixtureBuilder.Fixture stubbed(ExplanationFixtureBuilder.Fixture fixture) {
        when(scoringService.getReportScoringInputsForAttempt(TENANT_ID, ATTEMPT_ID)).thenReturn(fixture.answers());
        return fixture;
    }

    @Test
    void publishedExaminerTraitsAreExplainedWithZeroDifferenceAndRemainFrozen() {
        for (boolean gate : List.of(false, true)) {
            var fixture = stubbed(ExplanationFixtureBuilder.allCases().withExaminerTraits("DESCRIBE_IMAGE", gate).build());
            var aggregation = fixture.aggregationService(scoringService);
            publish(fixture, aggregation);
            var published = service(fixture, aggregation).explain(ATTEMPT_ID, HOST);
            var examiner = published.items().getLast();
            assertThat(examiner.scoreSource()).isEqualTo("EXAMINER");
            assertThat(examiner.traceKind()).isEqualTo("RUBRIC");
            assertThat(examiner.itemScore()).isEqualTo(examiner.trace().itemScore());
            assertThat(examiner.traitScores()).isEqualTo(fixture.answers().getLast().traitScores());
        }
    }

    private ExamResult publish(ExplanationFixtureBuilder.Fixture fixture, ScoreAggregationService aggregation) {
        List<ReportScoringAnswerView> inputs = fixture.answers();
        ReportScoreAggregation scored = aggregation.aggregateBatchFromInputs(Set.of(ATTEMPT_ID),
                Map.of(ATTEMPT_ID, inputs)).get(ATTEMPT_ID);
        String json = codec.encode(new ReportSnapshotCodec.Publication(UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-11-05T09:00:00Z"), 1, UUID.randomUUID()), scored, inputs,
                fixture.traceService().traceItems(inputs));
        ExamResult report = new ExamResult();
        report.setAttemptPublicId(ATTEMPT_ID);
        report.setTenantId(TENANT_ID);
        report.publishSnapshot(json, UUID.randomUUID(), UUID.randomUUID(), 1, Instant.parse("2026-11-05T09:00:00Z"));
        when(results.findByAttemptPublicId(ATTEMPT_ID)).thenReturn(Optional.of(report));
        org.mockito.Mockito.clearInvocations(scoringService);
        return report;
    }

    private static void assertSameScores(ScoreExplanationResponse explanation, ReportResponse report) {
        Map<String, Integer> reported = report.communicativeSkills().stream()
                .collect(Collectors.toMap(SkillScoreResponse::skill, score -> score.score() == null ? -1
                        : score.score()));
        Map<String, Integer> explained = explanation.skills().stream()
                .collect(Collectors.toMap(SkillExplanationResponse::skill, skill -> skill.score() == null ? -1
                        : skill.score()));
        assertThat(explained).isEqualTo(reported).hasSize(4);
        assertThat(explanation.overall().score()).isEqualTo(report.overall().score());
        assertThat(explanation.overall().totalWeight()).isEqualByComparingTo(report.overall().appliedWeight());
    }

    /** Zero difference between every recomputed item trace and the item score that was aggregated. */
    private static void assertItemTracesMatchItemScores(ScoreExplanationResponse explanation) {
        List<ItemExplanationResponse> traced = explanation.items().stream()
                .filter(item -> ItemTraceKind.RUBRIC.name().equals(item.traceKind())).toList();
        assertThat(traced).hasSizeGreaterThanOrEqualTo(13)
                .allSatisfy(item -> assertThat(item.trace().itemScore()).isEqualTo(item.itemScore()));
    }
}
