package com.pte.reporting.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.reporting.domain.enums.Skill;
import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes the 10-90 score summary for an attempt, weighted by the {@code
 * ScoreTemplate} pinned to its snapshot (spec FR-18/19/20) — replaces the
 * old "every SCORED answer weighs equally, task→skill via a hardcoded
 * config file" model. Pinned by {@code
 * AttemptService.getScoreContext} (never {@code
 * ScoreTemplateService.getActive}), so activating a new template never
 * changes an already-published attempt's score.
 *
 * <p>Formula per skill (FR-18): {@code 10 + 80 × Σ(w×avgRaw/100) / Σw},
 * summed only over the template's task types that (a) carry a non-zero
 * weight for that skill AND (b) have ≥1 SCORED answer for this attempt — a
 * weighted-but-not-yet-scored task type (e.g. AI grading still in flight)
 * is dropped from both numerator and denominator, never treated as {@code
 * avgRaw=0}. An exam item the student left blank (no answer row at all) is the
 * exception: it counts as raw 0 in its task type's average, and a task type
 * whose items were all left blank stays in the formula at 0. {@code Σw=0}
 * (nothing scored yet for that skill) always reports "insufficient data,"
 * never a division by zero.
 *
 * <p>Only skills in {@code testedSections} (FR-19) appear in the result at
 * all — an untested skill isn't reported even as insufficient data. Overall
 * (FR-20) is {@code null} — "not applicable," distinct from "insufficient
 * data" — unless all 4 skills were tested.
 */
@Service
public class ScoreAggregationService {

    private static final int SCALE_FLOOR = 10;
    private static final int SCALE_SPAN = 80;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL64;

    private static final Map<Skill, Function<ExamDefinitionItemResponse, BigDecimal>> SKILL_WEIGHT_ACCESSORS =
            new EnumMap<>(Skill.class);

    static {
        SKILL_WEIGHT_ACCESSORS.put(Skill.SPEAKING, ExamDefinitionItemResponse::speakingWeight);
        SKILL_WEIGHT_ACCESSORS.put(Skill.WRITING, ExamDefinitionItemResponse::writingWeight);
        SKILL_WEIGHT_ACCESSORS.put(Skill.READING, ExamDefinitionItemResponse::readingWeight);
        SKILL_WEIGHT_ACCESSORS.put(Skill.LISTENING, ExamDefinitionItemResponse::listeningWeight);
    }

    private final ScoringService scoringService;
    private final AttemptService attemptService;
    private final ExamDefinitionService examDefinitionService;

    public ScoreAggregationService(ScoringService scoringService, AttemptService attemptService,
                                   ExamDefinitionService examDefinitionService) {
        this.scoringService = scoringService;
        this.attemptService = attemptService;
        this.examDefinitionService = examDefinitionService;
    }

    public AttemptScoreSummary aggregate(UUID attemptPublicId, UUID tenantId) {
        return aggregateFromInputs(attemptPublicId, tenantId,
                scoringService.getReportScoringInputsForAttempt(tenantId, attemptPublicId));
    }

    public AttemptScoreSummary aggregateFromInputs(UUID attemptPublicId, UUID tenantId,
            List<ReportScoringAnswerView> answers) {
        AttemptScoreContextView context = attemptService.getScoreContext(attemptPublicId);
        ExamDefinitionResponse template = examDefinitionService.getByPublicId(context.examDefinitionPublicId());
        return aggregateWithScores(attemptPublicId,
                averageScoresByTaskType(answers, ReportScoringAnswerView::selectedScore,
                        context.expectedItemsByTaskType()),
                context, template.items());
    }

    /**
     * The Host's live view before publication: answers without a chosen score source count by their AI score.
     * Published reports never use this — they keep {@link #aggregate}'s selected-score-only rule.
     */
    public ProvisionalScoreSummary aggregateProvisional(UUID attemptPublicId, UUID tenantId) {
        List<ReportScoringAnswerView> answers = scoringService.getReportScoringInputsForAttempt(tenantId,
                attemptPublicId);
        AttemptScoreContextView context = attemptService.getScoreContext(attemptPublicId);
        ExamDefinitionResponse template = examDefinitionService.getByPublicId(context.examDefinitionPublicId());
        AttemptScoreSummary summary = aggregateWithScores(attemptPublicId,
                averageScoresByTaskType(answers, ReportScoringAnswerView::provisionalScore,
                        context.expectedItemsByTaskType()),
                context, template.items());
        boolean usesUnselected = answers.stream()
                .anyMatch(answer -> answer.selectedScore() == null && answer.provisionalScore() != null);
        return new ProvisionalScoreSummary(summary, usesUnselected);
    }

    /** Loads pinned contexts/templates in batches before aggregating a session cohort. */
    public Map<UUID, ReportScoreAggregation> aggregateBatchFromInputs(Set<UUID> attemptPublicIds,
            Map<UUID, List<ReportScoringAnswerView>> answersByAttempt) {
        if (attemptPublicIds == null || attemptPublicIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AttemptScoreContextView> contexts = attemptService.getScoreContexts(attemptPublicIds);
        Set<UUID> templateIds = contexts.values().stream()
                .map(AttemptScoreContextView::examDefinitionPublicId).collect(Collectors.toSet());
        Map<UUID, ExamDefinitionResponse> templates = examDefinitionService.getByPublicIds(templateIds);
        Map<UUID, ReportScoreAggregation> results = new HashMap<>();
        for (UUID attemptPublicId : attemptPublicIds) {
            AttemptScoreContextView context = contexts.get(attemptPublicId);
            ExamDefinitionResponse template = templates.get(context.examDefinitionPublicId());
            AttemptScoreSummary summary = aggregateWithScores(attemptPublicId,
                    averageScoresByTaskType(answersByAttempt.getOrDefault(attemptPublicId, List.of()),
                            ReportScoringAnswerView::selectedScore, context.expectedItemsByTaskType()),
                    context, template.items());
            results.put(attemptPublicId, new ReportScoreAggregation(context, summary));
        }
        return Map.copyOf(results);
    }

    private AttemptScoreSummary aggregateWithScores(UUID attemptPublicId, Map<String, BigDecimal> avgRawByTaskType,
            AttemptScoreContextView context, List<ExamDefinitionItemResponse> templateItems) {
        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            if (context.testedSections().contains(skill.name())) {
                skillScores.put(skill, computeWeighted(templateItems, avgRawByTaskType, SKILL_WEIGHT_ACCESSORS.get(skill)));
            }
        }

        boolean allFourSkillsTested = Set.of("SPEAKING", "WRITING", "READING", "LISTENING")
                .stream().allMatch(section -> context.testedSections().contains(section));
        SkillScore overall = allFourSkillsTested
                ? computeWeighted(templateItems, avgRawByTaskType, ExamDefinitionItemResponse::overallWeight)
                : null;

        return new AttemptScoreSummary(overall, skillScores);
    }

    /**
     * Only answers with a score from {@code scoreOf} contribute — the selected publishable score for reports,
     * the provisional one for the Host's live view. Objective scores are supplied unchanged by scoring.
     */
    private Map<String, BigDecimal> averageScoresByTaskType(List<ReportScoringAnswerView> answers,
            Function<ReportScoringAnswerView, Integer> scoreOf, Map<String, Integer> expectedItemsByTaskType) {
        Map<String, Integer> expected = new HashMap<>();
        expectedItemsByTaskType.forEach((taskType, count) ->
                expected.merge(TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskType), count, Integer::sum));
        Map<String, Long> rowsByTaskType = answers.stream().collect(Collectors.groupingBy(
                answer -> TaskTypeCodeCompatibility.normalizeTaskTypeKey(answer.taskType()), Collectors.counting()));
        Map<String, List<ReportScoringAnswerView>> scoredByTaskType = answers.stream()
                .filter(answer -> scoreOf.apply(answer) != null)
                .collect(Collectors.groupingBy(answer ->
                        TaskTypeCodeCompatibility.normalizeTaskTypeKey(answer.taskType())));
        Set<String> taskTypes = new java.util.HashSet<>(scoredByTaskType.keySet());
        taskTypes.addAll(expected.keySet());
        Map<String, BigDecimal> result = new HashMap<>();
        for (String taskType : taskTypes) {
            List<ReportScoringAnswerView> scored = scoredByTaskType.getOrDefault(taskType, List.of());
            // An item with no answer row at all was left blank: it counts as 0. A row that exists but has no score
            // (unscored task, AI still running) is not a blank and stays out of the average.
            long blanks = Math.max(0, expected.getOrDefault(taskType, 0) - rowsByTaskType.getOrDefault(taskType, 0L));
            long divisor = scored.size() + blanks;
            if (divisor == 0) {
                continue;
            }
            BigDecimal sum = scored.stream()
                    .map(a -> BigDecimal.valueOf(scoreOf.apply(a)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(taskType, sum.divide(BigDecimal.valueOf(divisor), MATH_CONTEXT));
        }
        return result;
    }

    /** FR-18, applied with whichever weight column {@code weightAccessor} selects (one of the 4 skills, or Overall). */
    private SkillScore computeWeighted(List<ExamDefinitionItemResponse> templateItems,
                                       Map<String, BigDecimal> avgRawByTaskType,
                                       Function<ExamDefinitionItemResponse, BigDecimal> weightAccessor) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (ExamDefinitionItemResponse item : templateItems) {
            BigDecimal weight = weightAccessor.apply(item);
            if (weight == null || weight.signum() <= 0) {
                continue;
            }
            BigDecimal avgRaw = avgRawByTaskType.get(
                    TaskTypeCodeCompatibility.normalizeTaskTypeKey(item.taskType()));
            if (avgRaw == null) {
                continue; // Weighted but not yet SCORED — excluded from both Σw and the numerator, never avgRaw=0.
            }
            weightedSum = weightedSum.add(weight.multiply(avgRaw, MATH_CONTEXT).divide(HUNDRED, MATH_CONTEXT));
            totalWeight = totalWeight.add(weight);
        }
        if (totalWeight.signum() <= 0) {
            return SkillScore.insufficientData(totalWeight);
        }
        BigDecimal ratio = weightedSum.divide(totalWeight, MATH_CONTEXT);
        BigDecimal scaled = BigDecimal.valueOf(SCALE_FLOOR).add(BigDecimal.valueOf(SCALE_SPAN).multiply(ratio, MATH_CONTEXT));
        return SkillScore.of(scaled.setScale(0, RoundingMode.HALF_UP).intValue(), totalWeight);
    }
}
