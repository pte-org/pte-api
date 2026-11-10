package com.pte.reporting.internal.service;

import com.pte.reporting.internal.service.explain.ExplanationFixtureBuilder;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ItemTraceKind;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExaminerTraitSnapshotTest {
    private final ReportSnapshotCodec codec = new ReportSnapshotCodec(JsonMapper.builder().findAndAddModules().build());
    /** Pearson RA/RS have no Content/Form zero gate, so only the seven gated task types get the zero case. */
    private static final Set<String> UNGATED = Set.of("READ_ALOUD", "REPEAT_SENTENCE");

    static Stream<Arguments> rubricTasks() {
        return Stream.of("READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE", "RE_TELL_LECTURE", "RESPOND_TO_A_SITUATION",
                "SUMMARIZE_GROUP_DISCUSSION", "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "SUMMARIZE_SPOKEN_TEXT")
                .flatMap(task -> UNGATED.contains(task) ? Stream.of(Arguments.of(task, false))
                        : Stream.of(Arguments.of(task, false), Arguments.of(task, true)));
    }

    @ParameterizedTest(name = "{0} zeroContent={1}") @MethodSource("rubricTasks")
    void selectedExaminerTraitsAreFrozenWithZeroDifferenceFromThePublishedItemTrace(String task, boolean zero) {
        var fixture = ExplanationFixtureBuilder.allCases().withExaminerTraits(task, zero).build();
        var aggregation = fixture.aggregationService(mock(ScoringService.class)).aggregateBatchFromInputs(
                Set.of(ExplanationFixtureBuilder.ATTEMPT_ID), Map.of(ExplanationFixtureBuilder.ATTEMPT_ID, fixture.answers()))
                .get(ExplanationFixtureBuilder.ATTEMPT_ID);
        var traces = fixture.traceService().traceItems(fixture.answers());
        var publication = new ReportSnapshotCodec.Publication(UUID.randomUUID(), UUID.randomUUID(),
                com.pte.TestClocks.fixed().instant(), 1, UUID.randomUUID());
        String stored = codec.encode(publication, aggregation, fixture.answers(), traces);
        var snapshot = codec.decode(stored);
        var saved = snapshot.scoreInputs().getLast();
        assertThat(snapshot.schemaVersion()).isEqualTo(2);
        assertThat(saved.selectedScoreSource()).isEqualTo("EXAMINER");
        assertThat(saved.traitScores()).isEqualTo(fixture.answers().getLast().traitScores());
        assertThat(saved.rubricVersionPublicId()).isEqualTo(fixture.pinnedVersion().getPublicId());
        assertThat(saved.traceKind()).isEqualTo(ItemTraceKind.RUBRIC);
        assertThat(saved.selectedScore() - saved.itemTrace().itemScore()).isZero();
        if (zero) { assertThat(saved.selectedScore()).isZero(); assertThat(saved.itemTrace().gateTriggeredBy()).isNotNull(); }
        org.springframework.test.util.ReflectionTestUtils.setField(fixture.pinnedVersion(), "status",
                com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus.RETIRED);
        assertThat(codec.decode(stored).scoreInputs().getLast()).isEqualTo(saved);
        assertThat(snapshot.scoreInputs()).filteredOn(input -> input.traceKind() == ItemTraceKind.EXAMINER_SCORE)
                .allSatisfy(input -> assertThat(input.traitScores()).isEmpty());
    }
}
