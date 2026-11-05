package com.pte.scoring.internal.service.examiner.trait;

import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.CriteriaScores;
import com.pte.scoring.domain.*;
import com.pte.scoring.domain.enums.*;
import com.pte.scoring.internal.rubric.service.ResolvedRubric;
import com.pte.scoring.internal.rubric.service.RubricVersionFixtures;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.internal.vendor.gemini.FormCheck;
import com.pte.scoring.rubric.*;
import com.pte.scoring.support.EvaluationFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExaminerTraitScoreResolverTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final ExaminerTraitRubricReader reader = mock(ExaminerTraitRubricReader.class);
    private final AnswerContentReader content = mock(AnswerContentReader.class);
    private final ExaminerTraitScoreResolver service = new ExaminerTraitScoreResolver(reader, new ExaminerTraitValidator(), content, json);
    private final UUID version = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(strings = { "READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE", "RE_TELL_LECTURE",
            "RESPOND_TO_A_SITUATION", "SUMMARIZE_GROUP_DISCUSSION", "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "SUMMARIZE_SPOKEN_TEXT" })
    void nineTaskTypesUseThePinnedScorerAndRejectInvalidHumanTraits(String task) {
        var fixture = fixture(task);
        var result = service.resolve(fixture.answer(), fixture.request(), null);
        assertThat(result.scoreTrace()).isEqualTo(RubricScorer.score(fixture.rubric(),
                new TraitScoreSet(result.traits(), task.equals("READ_ALOUD") ? 30 : null)));
        assertThat(result.itemScore()).isEqualTo(result.scoreTrace().itemScore());
        assertThat(CriteriaScores.read(json, result.traitsJson()).traits()).isEqualTo(result.traits());
        assertThat(result.rubricVersionPublicId()).isEqualTo(version);
        var missing = new LinkedHashMap<>(fixture.request());
        String first = fixture.rubric().task().traits().stream().filter(t -> t.source() != TraitSource.AZURE && !t.key().equals("FORM")).findFirst().orElseThrow().key();
        missing.remove(first);
        assertThatThrownBy(() -> service.resolve(fixture.answer(), missing, null)).hasMessage("EXAMINER_TRAIT_INVALID");
        for (BigDecimal invalid : List.of(BigDecimal.valueOf(-1), BigDecimal.valueOf(1000), new BigDecimal("1.5"))) {
            var bad = new LinkedHashMap<>(fixture.request()); bad.put(first, invalid);
            assertThatThrownBy(() -> service.resolve(fixture.answer(), bad, null)).hasMessage("EXAMINER_TRAIT_INVALID");
        }
        assertThatThrownBy(() -> service.resolve(fixture.answer(), null, 80)).hasMessage("EXAMINER_TRAITS_REQUIRED");
        assertThatThrownBy(() -> service.resolve(fixture.answer(), fixture.request(), result.itemScore() == 100 ? 99 : 100))
                .hasMessage("EXAMINER_TRAIT_SCORE_MISMATCH");
    }

    @Test void nullAzureUsesOnlyMatchingPublishableAiAndRejectsOverrides() {
        var f = fixture("DESCRIBE_IMAGE");
        assertThat(service.resolve(f.answer(), f.request(), null).traits()).containsEntry("PRONUNCIATION", 3);
        var override = new LinkedHashMap<>(f.request()); override.put("PRONUNCIATION", BigDecimal.ONE);
        assertThatThrownBy(() -> service.resolve(f.answer(), override, null)).hasMessage("EXAMINER_MACHINE_TRAIT_READ_ONLY");
        f.answer().recordDetail(CriteriaScores.pearson(UUID.randomUUID(), Map.of("PRONUNCIATION", 3, "ORAL_FLUENCY", 3)).toJson(json), null, null, null, false, version);
        assertThatThrownBy(() -> service.resolve(f.answer(), f.request(), null)).hasMessage("EXAMINER_AI_TRAITS_UNAVAILABLE");
        f.answer().recordDetail(null, null, null, null, false, version);
        assertThatThrownBy(() -> service.resolve(f.answer(), f.request(), null)).hasMessage("EXAMINER_AI_TRAITS_UNAVAILABLE");
    }
    @Test void formCannotBeSubmittedAndZeroContentGatesWithoutDiscardingOtherTraits() {
        var f = fixture("WRITE_ESSAY");
        var input = new LinkedHashMap<>(f.request()); input.put("FORM", BigDecimal.ONE);
        assertThatThrownBy(() -> service.resolve(f.answer(), input, null)).hasMessage("EXAMINER_TRAIT_UNKNOWN");
        input.remove("FORM"); input.put("CONTENT", BigDecimal.ZERO);
        var result = service.resolve(f.answer(), input, null);
        assertThat(result.itemScore()).isZero(); assertThat(result.scoreTrace().gateTriggeredBy()).isEqualTo("CONTENT");
        assertThat(result.traits().get("GRAMMAR")).isPositive();
        assertThat(result.traits().get("FORM")).isEqualTo(f.rubric().effectiveFormRule().bandFor(FormCheck.measure(f.submission().payload())));
    }
    @Test void oversizedMapsAndUnknownKeysFailWithoutClamping() {
        var f = fixture("DESCRIBE_IMAGE");
        var huge = new LinkedHashMap<>(f.request()); for (int i = 0; i < 17; i++) huge.put("X" + i, BigDecimal.ONE);
        assertThatThrownBy(() -> service.resolve(f.answer(), huge, null)).hasMessage("EXAMINER_TRAIT_COUNT_INVALID");
        var unknown = new LinkedHashMap<>(f.request()); unknown.put("UNKNOWN", BigDecimal.ONE);
        assertThatThrownBy(() -> service.resolve(f.answer(), unknown, null)).hasMessage("EXAMINER_TRAIT_UNKNOWN");
    }
    @Test void storedTraitsSurviveAiChangeAndSubmittedRowsCannotBeMutated() {
        var f = fixture("DESCRIBE_IMAGE"); var computed = service.resolve(f.answer(), f.request(), null);
        var saved = ExaminerEvaluation.draft(f.answer().getTenantId(), f.answer().getSessionPublicId(), f.answer().getAttemptPublicId(),
                f.answer().getAnswerPublicId(), UUID.randomUUID(), 1L, computed.itemScore(), null, ExaminerDecisionKind.MANUAL, "rv", null, com.pte.TestClocks.fixed().instant());
        saved.recordTraitScores(computed.traitsJson(), version);
        when(reader.stored(version, "DESCRIBE_IMAGE")).thenReturn(new ResolvedRubric(version, f.rubric()));
        f.answer().recordDetail(null, null, null, null, false, version);
        assertThat(service.stored(f.answer(), saved)).isEqualTo(computed);
        saved.submit(computed.itemScore(), com.pte.TestClocks.fixed().instant(), ExaminerDecisionKind.MANUAL, null, null, "rv", UUID.randomUUID(), "fp");
        assertThatThrownBy(() -> saved.recordTraitScores("{}", UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }
    @Test void legacySubmittedScoresRemainReadableButLegacyRubricDraftsCannotFinalize() {
        var f = fixture("DESCRIBE_IMAGE");
        var draft = ExaminerEvaluation.draft(f.answer().getTenantId(), f.answer().getSessionPublicId(), f.answer().getAttemptPublicId(),
                f.answer().getAnswerPublicId(), UUID.randomUUID(), 1L, 70, null, null, null, null, com.pte.TestClocks.fixed().instant());
        assertThatThrownBy(() -> service.stored(f.answer(), draft)).hasMessage("EXAMINER_TRAITS_REQUIRED");
        draft.submit(70, com.pte.TestClocks.fixed().instant(), null, null, null, null, null, null);
        assertThat(service.stored(f.answer(), draft).itemScore()).isEqualTo(70);
        assertThat(service.stored(f.answer(), draft).scoreTrace()).isNull();
    }
    @Test void nonRubricTaskKeepsTheTotalScoreContract() {
        var answer = EvaluationFixtures.ai("CUSTOM_AI_TASK", ScoringMethod.AI_SPEECH);
        assertThat(service.resolve(answer, null, 65)).isEqualTo(ExaminerTraitScoreResult.legacy(65));
        verifyNoInteractions(reader, content);
    }
    @Test void schemaUsesPinnedScalesAndDisallowsMechanicalOrAzureEditing() {
        var f = fixture("WRITE_ESSAY");
        when(reader.readMany(List.of(f.answer()))).thenReturn(Map.of(f.answer().getAnswerPublicId(), new ResolvedRubric(version, f.rubric())));
        var schema = service.schemas(List.of(f.answer()), Map.of(f.answer().getAnswerPublicId(), f.submission())).get(f.answer().getAnswerPublicId());
        assertThat(schema.rubricVersionPublicId()).isEqualTo(version);
        assertThat(schema.traits()).filteredOn(t -> t.key().equals("FORM")).allSatisfy(t -> { assertThat(t.editable()).isFalse(); assertThat(t.useAiWhenNull()).isFalse(); });
        assertThat(schema.traits()).filteredOn(t -> t.key().equals("CONTENT")).allSatisfy(t -> { assertThat(t.editable()).isTrue(); assertThat(t.levels()).isNotEmpty(); });
    }

    private Fixture fixture(String task) {
        var rubric = new EffectiveRubric(RubricVersionFixtures.CATALOG.find(task).orElseThrow(), RubricVersionFixtures.CATALOG.defaultParameters(task));
        var answer = EvaluationFixtures.ai(task, ScoringMethod.AI_SPEECH);
        answer.markAiScored(75, AiProviderCategory.REAL, "fixture", "fixture", "v1", com.pte.TestClocks.fixed().instant());
        Map<String, Integer> ai = new LinkedHashMap<>(); Map<String, BigDecimal> requested = new LinkedHashMap<>();
        for (var trait : rubric.task().traits()) {
            int value = Math.min(3, rubric.maxOf(trait, task.equals("READ_ALOUD") ? 30 : null)); ai.put(trait.key(), value);
            if (!trait.key().equals("FORM")) requested.put(trait.key(), trait.source() == TraitSource.AZURE ? null : BigDecimal.valueOf(value));
        }
        answer.recordDetail(CriteriaScores.pearson(version, ai).toJson(json), null, null, "{\"wordCount\":30}", false, version);
        var submission = new SubmittedAnswerView(answer.getAnswerPublicId(), answer.getAttemptPublicId(), answer.getItemPublicId(),
                answer.getSessionPublicId(), answer.getTenantId(), answer.getExamDefinitionPublicId(), task,
                "A good essay " + "word ".repeat(220) + ".", null, null, false, "word ".repeat(30), null, null, null, null, "WRITING", null, null, null, null, null);
        when(reader.read(answer)).thenReturn(new ResolvedRubric(version, rubric));
        when(content.one(answer.getAnswerPublicId(), answer.getTenantId())).thenReturn(Optional.of(submission));
        return new Fixture(answer, rubric, requested, submission);
    }
    private record Fixture(AiEvaluation answer, EffectiveRubric rubric, Map<String, BigDecimal> request, SubmittedAnswerView submission) { }
}
