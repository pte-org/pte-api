package com.pte.scoring.internal.rubric.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.scoring.RubricTraceService;
import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.dto.response.ItemScoreTraceView;
import com.pte.scoring.dto.response.ItemTraceKind;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.rubric.RubricScorer;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.ScoreTrace;
import com.pte.scoring.rubric.TraitContribution;
import com.pte.scoring.rubric.TraitScoreSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CATALOG;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CODEC;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.child;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.defaultsJson;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.editTask;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FR-05: item scores are recomputed only from stored trait scores, the answer's own pinned version row and
 * RubricScorer — never from code defaults, never through an AI vendor — and every answer gets an explanation kind.
 */
class RubricRescoreServiceTest {

    private static final String DI = "DESCRIBE_IMAGE";
    private static final String RA = "READ_ALOUD";

    private final ScoringRubricVersionRepository versions = mock(ScoringRubricVersionRepository.class);
    private final RubricResolver resolver = new RubricResolver(versions, CATALOG, CODEC,
            mock(ExamDefinitionService.class));
    private final RubricRescoreService service = new RubricRescoreService(versions, resolver, CATALOG);
    private final ScoringRubricVersion pinned = stored(RubricVersionStatus.RETIRED, defaultsJson());

    @Test
    void aRubricItemIsRecomputedToTheStoredScoreWithItsTraitContributions() {
        Map<String, Integer> traits = Map.of(RubricTraits.CONTENT, 4, RubricTraits.PRONUNCIATION, 3, RubricTraits.ORAL_FLUENCY, 2);
        int stored = RubricScorer.score(resolver.effectiveFor(pinned, DI), new TraitScoreSet(traits, null))
                .itemScore();

        ItemScoreTraceView view = trace(aiAnswer(DI, stored, traits, pinned.getPublicId(), null));

        assertThat(view.kind()).isEqualTo(ItemTraceKind.RUBRIC);
        assertThat(view.trace().itemScore()).isEqualTo(stored).isEqualTo(56);
        assertThat(view.trace().gateTriggeredBy()).isNull();
        assertThat(view.trace().contributions()).extracting(TraitContribution::max).containsExactly(6, 5, 5);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 30, 80})
    void readAloudUsesTheStoredPassageLengthAsTheContentMaximum(int n) {
        int content = n - n / 5;
        Map<String, Integer> traits = Map.of(RubricTraits.CONTENT, content, RubricTraits.PRONUNCIATION, 4, RubricTraits.ORAL_FLUENCY, 3);
        int stored = RubricScorer.score(resolver.effectiveFor(pinned, RA), new TraitScoreSet(traits, n)).itemScore();

        ScoreTrace trace = trace(aiAnswer(RA, stored, traits, pinned.getPublicId(), n)).trace();

        assertThat(trace.itemScore()).isEqualTo(stored);
        assertThat(trace.contributions().getFirst().max()).isEqualTo(n);
        assertThat(trace.contributions().getFirst().achieved()).isEqualTo(content);
    }

    @Test
    void aGateTraitAtZeroZeroesTheItemAndIsNamed() {
        Map<String, Integer> traits = Map.of(RubricTraits.CONTENT, 0, RubricTraits.PRONUNCIATION, 5, RubricTraits.ORAL_FLUENCY, 5);

        ScoreTrace trace = trace(aiAnswer(DI, 0, traits, pinned.getPublicId(), null)).trace();

        assertThat(trace.itemScore()).isZero();
        assertThat(trace.gateTriggeredBy()).isEqualTo(RubricTraits.CONTENT);
    }

    @Test
    void theAnswersOwnPinnedVersionIsUsedNotTheCodeDefaults() {
        ScoringRubricVersion edited = stored(RubricVersionStatus.ACTIVE,
                editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_WEIGHTS).put(RubricTraits.CONTENT, 1)));
        Map<String, Integer> traits = Map.of(RubricTraits.CONTENT, 6, RubricTraits.PRONUNCIATION, 0, RubricTraits.ORAL_FLUENCY, 0);

        assertThat(trace(aiAnswer(DI, 0, traits, pinned.getPublicId(), null)).trace().itemScore()).isEqualTo(38);
        assertThat(trace(aiAnswer(DI, 0, traits, edited.getPublicId(), null)).trace().itemScore()).isEqualTo(9);
    }

    @Test
    void everyAnswerGetsItsExplanationKind() {
        ReportScoringAnswerView objective = answer("MC_READING_SINGLE", "OBJECTIVE", null, 1, Map.of(), null);
        ReportScoringAnswerView unscored = answer("PERSONAL_INTRODUCTION", "UNSCORED", null, null, Map.of(), null);
        // Legacy total-only examiner score (before trait scoring): shown as "examiner score, not decomposed".
        ReportScoringAnswerView examiner = answer(DI, "AI_SPEECH", "EXAMINER", 70, Map.of(), null);
        ReportScoringAnswerView notScored = answer(DI, "AI_SPEECH", null, null, Map.of(), null);
        ReportScoringAnswerView legacy = answer(DI, "AI_SPEECH", "AI", 55, Map.of(), null);
        ReportScoringAnswerView missingVersion = aiAnswer(DI, 50, Map.of(RubricTraits.CONTENT, 4, RubricTraits.PRONUNCIATION, 3,
                RubricTraits.ORAL_FLUENCY, 2), UUID.randomUUID(), null);
        ReportScoringAnswerView unknownTrait = aiAnswer(DI, 50, Map.of("NOT_A_TRAIT", 1), pinned.getPublicId(), null);
        when(versions.findByPublicId(missingVersion.rubricVersionPublicId())).thenReturn(Optional.empty());

        Map<UUID, ItemScoreTraceView> traces = service.traceItems(List.of(objective, unscored, examiner, notScored,
                legacy, missingVersion, unknownTrait));

        assertThat(traces.values()).extracting(ItemScoreTraceView::kind).containsExactly(ItemTraceKind.OBJECTIVE,
                ItemTraceKind.UNSCORED, ItemTraceKind.EXAMINER_SCORE, ItemTraceKind.NOT_SCORED,
                ItemTraceKind.LEGACY_NO_TRACE, ItemTraceKind.TRACE_UNAVAILABLE, ItemTraceKind.TRACE_UNAVAILABLE);
        assertThat(traces.get(objective.answerPublicId()).objectiveRuleCode()).isNotBlank();
        assertThat(traces.values()).filteredOn(view -> view.kind() != ItemTraceKind.RUBRIC)
                .allSatisfy(view -> assertThat(view.trace()).isNull());
    }

    @Test
    void aVersionIsReadOncePerCallHoweverManyAnswersShareIt() {
        Map<String, Integer> traits = Map.of(RubricTraits.CONTENT, 4, RubricTraits.PRONUNCIATION, 3, RubricTraits.ORAL_FLUENCY, 2);

        service.traceItems(List.of(aiAnswer(DI, 56, traits, pinned.getPublicId(), null),
                aiAnswer(DI, 56, traits, pinned.getPublicId(), null), aiAnswer(DI, 56, traits, pinned.getPublicId(),
                        null)));

        verify(versions, times(1)).findByPublicId(pinned.getPublicId());
    }

    /** Recomputation reaches no AI vendor: nothing on the explanation path depends on a vendor client. */
    @Test
    void theRescorePathHasNoDependencyOnAnyVendorClient() {
        Set<Class<?>> seen = new HashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>(List.of(RubricTraceService.class));
        while (!pending.isEmpty()) {
            Class<?> type = pending.pop();
            if (!seen.add(type)) {
                continue;
            }
            assertThat(type.getPackageName()).doesNotContain(".vendor").doesNotContain(".messaging");
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                for (Class<?> dependency : constructor.getParameterTypes()) {
                    if (dependency.getPackageName().startsWith("com.pte")) {
                        pending.push(dependency);
                    }
                }
            }
        }
        assertThat(seen).contains(RubricRescoreService.class, PinnedRubricParametersService.class);
    }

    private ItemScoreTraceView trace(ReportScoringAnswerView answer) {
        return service.traceItems(List.of(answer)).get(answer.answerPublicId());
    }

    private ScoringRubricVersion stored(RubricVersionStatus status, String json) {
        ScoringRubricVersion version = version(status, json);
        when(versions.findByPublicId(version.getPublicId())).thenReturn(Optional.of(version));
        return version;
    }

    private static ReportScoringAnswerView aiAnswer(String taskType, int score, Map<String, Integer> traits,
            UUID version, Integer wordCount) {
        return new ReportScoringAnswerView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), taskType,
                "SPEAKING", "AI_SPEECH", score, "REAL", "p", "m", "v", null, "AI", score, true, null, 0, score,
                traits, version, wordCount);
    }

    private static ReportScoringAnswerView answer(String taskType, String method, String source, Integer score,
            Map<String, Integer> traits, UUID version) {
        return new ReportScoringAnswerView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), taskType,
                "READING", method, score, "REAL", "p", "m", "v", null, source, score, true, null, 0, score, traits,
                version, null);
    }
}
