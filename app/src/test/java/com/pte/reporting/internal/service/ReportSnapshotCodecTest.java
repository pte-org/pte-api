package com.pte.reporting.internal.service;

import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.reporting.internal.service.explain.ExplanationFixtureBuilder;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ItemScoreTraceView;
import com.pte.scoring.dto.response.ItemTraceKind;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Schema 2 round-trips traits, rubric versions and traces; schema 1 still decodes; any other schema is refused. */
class ReportSnapshotCodecTest {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private final ReportSnapshotCodec codec = new ReportSnapshotCodec(JSON);

    @Test
    void schemaTwoRoundTripKeepsHeaderScoresTraitsVersionsAndTraces() {
        ExplanationFixtureBuilder.Fixture fixture = ExplanationFixtureBuilder.allCases().build();
        ReportScoreAggregation aggregation = fixture.aggregationService(mock(ScoringService.class))
                .aggregateBatchFromInputs(Set.of(ExplanationFixtureBuilder.ATTEMPT_ID),
                        Map.of(ExplanationFixtureBuilder.ATTEMPT_ID, fixture.answers()))
                .get(ExplanationFixtureBuilder.ATTEMPT_ID);
        Map<UUID, ItemScoreTraceView> traces = fixture.traceService().traceItems(fixture.answers());
        ReportSnapshotCodec.Publication publication = new ReportSnapshotCodec.Publication(UUID.randomUUID(),
                UUID.randomUUID(), Instant.parse("2026-11-05T08:00:00Z"), 4, UUID.randomUUID());

        ReportSnapshot snapshot = codec.decode(codec.encode(publication, aggregation, fixture.answers(), traces));

        assertThat(snapshot.schemaVersion()).isEqualTo(ReportingConstants.SNAPSHOT_SCHEMA_VERSION_TRACED);
        assertThat(ReportSnapshotCodec.traceAvailable(snapshot)).isTrue();
        assertThat(snapshot.publicationPublicId()).isEqualTo(publication.publicationPublicId());
        assertThat(snapshot.publishedAt()).isEqualTo(publication.publishedAt());
        assertThat(snapshot.cohortSize()).isEqualTo(4);
        assertThat(snapshot.examSnapshotPublicId()).isEqualTo(publication.examSnapshotPublicId());
        assertThat(snapshot.scoreTemplatePublicId()).isEqualTo(ExplanationFixtureBuilder.TEMPLATE_ID);
        assertThat(snapshot.scoreTemplateVersion()).isEqualTo(3);
        assertThat(snapshot.rubricVersionPublicId()).isEqualTo(fixture.pinnedVersion().getPublicId());
        assertThat(snapshot.scoreSummary()).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class).isEqualTo(aggregation.summary());
        assertThat(snapshot.aggregationTrace()).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class).isEqualTo(aggregation.trace());
        assertThat(snapshot.scoreInputs()).hasSize(fixture.answers().size());
        for (int i = 0; i < fixture.answers().size(); i++) {
            ReportScoringAnswerView input = fixture.answers().get(i);
            ReportSnapshotScoreInput saved = snapshot.scoreInputs().get(i);
            ItemScoreTraceView trace = traces.get(input.answerPublicId());
            assertThat(saved.answerPublicId()).isEqualTo(input.answerPublicId());
            assertThat(saved.selectedScore()).isEqualTo(input.selectedScore());
            assertThat(saved.traitScores()).isEqualTo(input.traitScores());
            assertThat(saved.rubricVersionPublicId()).isEqualTo(input.rubricVersionPublicId());
            assertThat(saved.wordCount()).isEqualTo(input.wordCount());
            assertThat(saved.traceKind()).isEqualTo(trace.kind());
            assertThat(saved.objectiveRuleCode()).isEqualTo(trace.objectiveRuleCode());
            assertThat(saved.itemTrace()).usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class).isEqualTo(trace.trace());
        }
        assertThat(snapshot.scoreInputs()).extracting(ReportSnapshotScoreInput::traceKind)
                .contains(ItemTraceKind.RUBRIC, ItemTraceKind.OBJECTIVE, ItemTraceKind.EXAMINER_SCORE,
                        ItemTraceKind.LEGACY_NO_TRACE);
    }

    @Test
    void schemaOneStillDecodesWithTheNewFieldsEmptyAndNoTrace() {
        ReportSnapshot snapshot = codec.decode(ExplanationFixtureBuilder.v1SnapshotJson());

        assertThat(snapshot.schemaVersion()).isEqualTo(ReportingConstants.SNAPSHOT_SCHEMA_VERSION_LEGACY);
        assertThat(ReportSnapshotCodec.traceAvailable(snapshot)).isFalse();
        assertThat(snapshot.rubricVersionPublicId()).isNull();
        assertThat(snapshot.aggregationTrace()).isNull();
        assertThat(snapshot.scoreSummary().skillScores().get(Skill.WRITING).score()).isEqualTo(81);
        assertThat(snapshot.scoreSummary().skillScores().get(Skill.READING).sufficientData()).isFalse();
        ReportSnapshotScoreInput input = snapshot.scoreInputs().getFirst();
        assertThat(input.selectedScore()).isEqualTo(81);
        assertThat(input.selectedScoreSource()).isEqualTo("EXAMINER");
        assertThat(input.traitScores()).isNull();
        assertThat(input.traceKind()).isNull();
        assertThat(input.itemTrace()).isNull();
    }

    @Test
    void anyOtherSchemaVersionIsRejected() {
        for (int unsupported : List.of(0, 3, 99)) {
            ObjectNode json = (ObjectNode) JSON.readTree(ExplanationFixtureBuilder.v1SnapshotJson());
            json.put("schemaVersion", unsupported);

            assertThatThrownBy(() -> codec.decode(JSON.writeValueAsString(json)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(ReportingConstants.SNAPSHOT_VERSION_UNSUPPORTED);
        }
    }

    @Test
    void aMissingSnapshotIsUnavailable() {
        assertThatThrownBy(() -> codec.decode(" ")).hasMessage(ReportingConstants.SNAPSHOT_UNAVAILABLE);
    }
}
