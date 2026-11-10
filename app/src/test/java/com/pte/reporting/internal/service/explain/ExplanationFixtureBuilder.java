package com.pte.reporting.internal.service.explain;

import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.reporting.internal.service.ScoreAggregationService;
import com.pte.scoring.RubricTraceService;
import com.pte.scoring.ScoringService;
import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.internal.rubric.service.PinnedRubricParametersService;
import com.pte.scoring.internal.rubric.service.RubricRescoreService;
import com.pte.scoring.internal.rubric.service.RubricResolver;
import com.pte.scoring.internal.rubric.service.RubricVersionFixtures;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricScorer;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitScoreSet;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared fixture for score-explanation tests (plan quang-scoring-rubric-transparency phase 6, LOW-2; reusable by
 * phase 10): one attempt covering all 22 catalog task types, scored with the real catalog, the real pinned version-1
 * parameters and {@link RubricScorer}, plus the edge cases an explanation must survive — Read Aloud with N = 1, 30
 * and 80, a gate that zeroes an item, an examiner-sourced answer, a legacy row without Pearson traits, an item left
 * blank and, optionally, an AI answer whose source is not chosen yet. {@link #v1SnapshotJson} is a schema-1 snapshot
 * as published before explanations existed.
 */
public final class ExplanationFixtureBuilder {

    public static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    public static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-00000000b001");
    public static final UUID ATTEMPT_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    public static final UUID TEMPLATE_ID = UUID.fromString("00000000-0000-0000-0000-00000000d001");
    public static final String READ_ALOUD = "READ_ALOUD";
    public static final String DESCRIBE_IMAGE = "DESCRIBE_IMAGE";
    public static final String WRITE_ESSAY = "WRITE_ESSAY";
    public static final String RETELL_LECTURE = "RE_TELL_LECTURE";
    public static final String WRITE_FROM_DICTATION = "WRITE_FROM_DICTATION";
    private static final List<String> SKILLS = List.of("SPEAKING", "WRITING", "READING", "LISTENING");
    private static final String V1_RESOURCE = "reporting/report-snapshot-v1.json";

    private final ScoringRubricVersion pinned = RubricVersionFixtures.version(RubricVersionStatus.ACTIVE,
            RubricVersionFixtures.defaultsJson());
    private final List<ReportScoringAnswerView> answers = new ArrayList<>();
    private final Map<String, Integer> blanks = new LinkedHashMap<>();
    private int sequence;

    /** Every case: 22 task types, RA N = 1/30/80, a gate, an examiner score, a legacy row and one blank item. */
    public static ExplanationFixtureBuilder allCases() {
        return new ExplanationFixtureBuilder().withEveryTaskType().withReadAloudLengths(1, 30, 80)
                .withGateTriggered(DESCRIBE_IMAGE).withExaminerSource(WRITE_ESSAY, 70)
                .withLegacyRow(RETELL_LECTURE, 55).withBlank(WRITE_FROM_DICTATION, 1);
    }

    public ExplanationFixtureBuilder withEveryTaskType() {
        for (TaskRubric task : RubricVersionFixtures.CATALOG.all()) {
            if (task.isObjective()) {
                answers.add(answer(task.taskType(), "OBJECTIVE", 35 + (sequence * 7) % 60, null, Map.of(), null,
                        null));
            } else {
                Map<String, Integer> traits = traits(task.taskType(), wordCountFor(task, 12), false);
                answers.add(rubricAnswer(task.taskType(), traits, wordCountFor(task, 12)));
            }
        }
        return this;
    }

    public ExplanationFixtureBuilder withReadAloudLengths(int... wordCounts) {
        for (int n : wordCounts) {
            answers.add(rubricAnswer(READ_ALOUD, traits(READ_ALOUD, n, false), n));
        }
        return this;
    }

    public ExplanationFixtureBuilder withGateTriggered(String taskType) {
        answers.add(rubricAnswer(taskType, traits(taskType, null, true), null));
        return this;
    }

    /** Legacy total-only Examiner row; its AI traits must not be projected as human traits. */
    public ExplanationFixtureBuilder withExaminerSource(String taskType, int examinerScore) {
        Map<String, Integer> traits = traits(taskType, null, false);
        int ai = itemScore(taskType, traits, null);
        answers.add(new ReportScoringAnswerView(UUID.randomUUID(), ATTEMPT_ID, TEMPLATE_ID, taskType,
                sectionOf(taskType), methodOf(taskType), ai, "REAL", "gemini", "model", "v1", examinerScore,
                "EXAMINER", examinerScore, true, null, ++sequence, examinerScore, Map.of(), null, 210));
        return this;
    }

    /** Phase 10 human traits selected as the score of record; distinct from legacy totals. */
    private static final int READ_ALOUD_EXAMINER_WORDS = 30;

    public ExplanationFixtureBuilder withExaminerTraits(String taskType, boolean zeroContent) {
        Integer words = "READ_ALOUD".equals(taskType) ? READ_ALOUD_EXAMINER_WORDS : null;
        Map<String, Integer> values = traits(taskType, words, zeroContent);
        int score = itemScore(taskType, values, words);
        answers.add(new ReportScoringAnswerView(UUID.randomUUID(), ATTEMPT_ID, TEMPLATE_ID, taskType,
                sectionOf(taskType), methodOf(taskType), 99, "REAL", "fixture", "model", "v1", score,
                "EXAMINER", score, true, null, ++sequence, score, values, pinned.getPublicId(), words == null ? 210 : words));
        return this;
    }

    public ExplanationFixtureBuilder withLegacyRow(String taskType, int legacyScore) {
        answers.add(answer(taskType, methodOf(taskType), legacyScore, "AI", Map.of(), null, null));
        return this;
    }

    public ExplanationFixtureBuilder withBlank(String taskType, int blankItems) {
        blanks.merge(taskType, blankItems, Integer::sum);
        return this;
    }

    /** Live view only: an AI score counted provisionally because no source was chosen (not publishable). */
    public ExplanationFixtureBuilder withUnselectedAiAnswer(String taskType) {
        Map<String, Integer> traits = traits(taskType, null, false);
        int ai = itemScore(taskType, traits, null);
        answers.add(new ReportScoringAnswerView(UUID.randomUUID(), ATTEMPT_ID, TEMPLATE_ID, taskType,
                sectionOf(taskType), methodOf(taskType), ai, "REAL", "gemini", "model", "v1", null, null, null,
                false, "NO_SELECTED_SOURCE", ++sequence, ai, traits, pinned.getPublicId(), null));
        return this;
    }

    public Fixture build() {
        Map<String, Integer> expected = new LinkedHashMap<>();
        answers.forEach(answer -> expected.merge(answer.taskType(), 1, Integer::sum));
        blanks.forEach((taskType, count) -> expected.merge(taskType, count, Integer::sum));
        AttemptScoreContextView context = new AttemptScoreContextView(TEMPLATE_ID, 3, Set.copyOf(SKILLS), expected);
        ExamDefinitionResponse template = template();
        ScoringRubricVersionRepository versions = mock(ScoringRubricVersionRepository.class);
        when(versions.findByPublicId(pinned.getPublicId())).thenReturn(Optional.of(pinned));
        RubricResolver resolver = new RubricResolver(versions, RubricVersionFixtures.CATALOG,
                RubricVersionFixtures.CODEC, mock(ExamDefinitionService.class));
        RubricTraceService traceService = new RubricTraceService(
                new RubricRescoreService(versions, resolver, RubricVersionFixtures.CATALOG),
                new PinnedRubricParametersService(versions, resolver));
        return new Fixture(List.copyOf(answers), context, template, pinned, versions, traceService);
    }

    /** A schema-1 snapshot (scores only, no trace) as stored before score explanations. */
    public static String v1SnapshotJson() {
        try (InputStream in = ExplanationFixtureBuilder.class.getClassLoader().getResourceAsStream(V1_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(V1_RESOURCE + " not on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private ReportScoringAnswerView rubricAnswer(String taskType, Map<String, Integer> traits, Integer wordCount) {
        int score = itemScore(taskType, traits, wordCount);
        return answer(taskType, methodOf(taskType), score, "AI", traits, pinned.getPublicId(), wordCount);
    }

    private ReportScoringAnswerView answer(String taskType, String method, int score, String source,
            Map<String, Integer> traits, UUID version, Integer wordCount) {
        return new ReportScoringAnswerView(UUID.randomUUID(), ATTEMPT_ID, TEMPLATE_ID, taskType, sectionOf(taskType),
                method, score, "REAL", "gemini", "model", "v1",
                null, source, score, true, null, ++sequence, score, traits, version, wordCount);
    }

    /** Each trait two thirds of its maximum (at least 1); a gate case puts the first gate trait at 0. */
    private Map<String, Integer> traits(String taskType, Integer wordCount, boolean gate) {
        EffectiveRubric rubric = rubricOf(taskType);
        Map<String, Integer> traits = new LinkedHashMap<>();
        for (TraitDefinition trait : rubric.task().traits()) {
            int max = rubric.maxOf(trait, wordCount);
            traits.put(trait.key(), Math.max(1, (max * 2 + 2) / 3));
        }
        if (gate) {
            traits.put(rubric.task().gateTraits().stream().sorted().findFirst().orElseThrow(), 0);
        }
        return traits;
    }

    private int itemScore(String taskType, Map<String, Integer> traits, Integer wordCount) {
        return RubricScorer.score(rubricOf(taskType), new TraitScoreSet(traits, wordCount)).itemScore();
    }

    private EffectiveRubric rubricOf(String taskType) {
        return new RubricResolver(mock(ScoringRubricVersionRepository.class), RubricVersionFixtures.CATALOG,
                RubricVersionFixtures.CODEC, mock(ExamDefinitionService.class)).effectiveFor(pinned, taskType);
    }

    private static Integer wordCountFor(TaskRubric task, int n) {
        return task.traits().stream().anyMatch(trait -> trait.scale().isPerWord()) ? n : null;
    }

    private static String methodOf(String taskType) {
        return "SPEAKING".equals(sectionOf(taskType)) ? "AI_SPEECH" : "AI_TEXT";
    }

    static String sectionOf(String taskType) {
        Set<String> skills = RubricVersionFixtures.CATALOG.find(taskType).orElseThrow().skills();
        return SKILLS.stream().filter(skills::contains).findFirst().orElseThrow();
    }

    /** One template row per task type: weight 1, 1.5 or 2 on each of its skills (by catalog order), Overall 2. */
    private ExamDefinitionResponse template() {
        List<ExamDefinitionItemResponse> items = new ArrayList<>();
        int index = 0;
        for (TaskRubric task : RubricVersionFixtures.CATALOG.all()) {
            BigDecimal weight = BigDecimal.ONE.add(BigDecimal.valueOf(index++ % 3).multiply(new BigDecimal("0.5")));
            items.add(new ExamDefinitionItemResponse(task.taskType(), sectionOf(task.taskType()), index, 1, 3, 0, 0,
                    task.isObjective() ? "OBJECTIVE" : methodOf(task.taskType()), BigDecimal.valueOf(2),
                    weightIf(task, "SPEAKING", weight), weightIf(task, "WRITING", weight),
                    weightIf(task, "READING", weight), weightIf(task, "LISTENING", weight)));
        }
        return new ExamDefinitionResponse(TEMPLATE_ID, "PTE_STANDARD", 3, "PTE standard", "ACTIVE", null, items,
                "STANDARD_PTE", null, null, null, null, null, null, null, null, List.of(), List.of(),
                pinned.getPublicId());
    }

    private static BigDecimal weightIf(TaskRubric task, String skill, BigDecimal weight) {
        return task.skills().contains(skill) ? weight : BigDecimal.ZERO;
    }

    /** Everything an explanation test needs, built from one fixture. */
    public record Fixture(List<ReportScoringAnswerView> answers, AttemptScoreContextView context,
                          ExamDefinitionResponse template, ScoringRubricVersion pinnedVersion,
                          ScoringRubricVersionRepository versions, RubricTraceService traceService) {

        /** An aggregation service whose attempt/template reads return this fixture's pinned context. */
        public ScoreAggregationService aggregationService(ScoringService scoringService) {
            AttemptService attempts = mock(AttemptService.class);
            ExamDefinitionService definitions = mock(ExamDefinitionService.class);
            when(attempts.getScoreContext(ATTEMPT_ID)).thenReturn(context);
            when(attempts.getScoreContexts(any())).thenReturn(Map.of(ATTEMPT_ID, context));
            when(definitions.getByPublicId(TEMPLATE_ID)).thenReturn(template);
            when(definitions.getByPublicIds(any())).thenReturn(Map.of(TEMPLATE_ID, template));
            return new ScoreAggregationService(scoringService, attempts, definitions);
        }
    }
}
