package com.pte.reporting.internal.service.aggregation;

import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.scoring.dto.response.ReportScoringAnswerView;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The single computation behind every 10-90 score (FR-18/19/20) and its explanation (FR-07): every aggregate method
 * and the explanation read the numbers from the trace this core builds, so the explanation can never drift from the
 * published figures. Formula per skill: {@code 10 + 80 × Σ(w × avg / 100) / Σw}, rounded half-up, summed only over
 * the template's task types that carry a positive weight for that skill and have an average; a weighted task type
 * with nothing scored yet is excluded from both sums (never counted as 0), while an item left blank counts as 0 in
 * its task type's average. {@code Σw = 0} is insufficient data. Only tested skills are traced; Overall needs all four.
 */
public final class AggregationCore {

    private static final int SCALE_FLOOR = 10;
    private static final int SCALE_SPAN = 80;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL64;

    private static final Map<Skill, Function<ExamDefinitionItemResponse, BigDecimal>> SKILL_WEIGHTS =
            new EnumMap<>(Skill.class);

    static {
        SKILL_WEIGHTS.put(Skill.SPEAKING, ExamDefinitionItemResponse::speakingWeight);
        SKILL_WEIGHTS.put(Skill.WRITING, ExamDefinitionItemResponse::writingWeight);
        SKILL_WEIGHTS.put(Skill.READING, ExamDefinitionItemResponse::readingWeight);
        SKILL_WEIGHTS.put(Skill.LISTENING, ExamDefinitionItemResponse::listeningWeight);
    }

    private AggregationCore() {
    }

    /** {@code scoreOf} picks the selected score (reports) or the provisional one (the Host's live view). */
    public static AggregationTrace trace(List<ReportScoringAnswerView> answers,
            Function<ReportScoringAnswerView, Integer> scoreOf, AttemptScoreContextView context,
            ExamDefinitionResponse template) {
        List<TaskTypeAverageStep> averages = averages(answers, scoreOf, context.expectedItemsByTaskType());
        Map<String, BigDecimal> averageByTaskType = averages.stream()
                .collect(Collectors.toMap(TaskTypeAverageStep::taskType, TaskTypeAverageStep::average));
        List<ExamDefinitionItemResponse> items = template.items();
        Set<String> tested = ReportingConstants.TEMPLATE_POLICY_CUSTOM.equalsIgnoreCase(template.templatePolicy())
                ? testedSkills(context.testedSections(), items)
                : context.testedSections();
        Map<Skill, SkillTrace> skills = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            if (tested.contains(skill.name())) {
                skills.put(skill, weighted(items, averageByTaskType, SKILL_WEIGHTS.get(skill)));
            }
        }
        boolean allFourTested = Set.of(Skill.values()).stream().allMatch(skill -> tested.contains(skill.name()));
        SkillTrace overall = allFourTested
                ? weighted(items, averageByTaskType, ExamDefinitionItemResponse::overallWeight)
                : null;
        return new AggregationTrace(averages, skills, overall);
    }

    /**
     * Custom templates only: a skill is tested when the exam has a section of that name, or when a task in a tested
     * section carries weight for it — a host may drop every Reading task yet keep Reading weight on Writing tasks.
     */
    private static Set<String> testedSkills(Set<String> testedSections, List<ExamDefinitionItemResponse> items) {
        Set<String> tested = new HashSet<>(testedSections);
        for (Skill skill : Skill.values()) {
            Function<ExamDefinitionItemResponse, BigDecimal> weightOf = SKILL_WEIGHTS.get(skill);
            boolean weighted = items.stream()
                    .filter(item -> testedSections.contains(item.section()))
                    .map(weightOf)
                    .anyMatch(weight -> weight != null && weight.signum() > 0);
            if (weighted) {
                tested.add(skill.name());
            }
        }
        return tested;
    }

    private static List<TaskTypeAverageStep> averages(List<ReportScoringAnswerView> answers,
            Function<ReportScoringAnswerView, Integer> scoreOf, Map<String, Integer> expectedItemsByTaskType) {
        Map<String, Integer> expected = new HashMap<>();
        expectedItemsByTaskType.forEach((taskType, count) -> expected.merge(key(taskType), count, Integer::sum));
        Map<String, Long> rowsByTaskType = answers.stream()
                .collect(Collectors.groupingBy(answer -> key(answer.taskType()), Collectors.counting()));
        Map<String, List<ReportScoringAnswerView>> scoredByTaskType = answers.stream()
                .filter(answer -> scoreOf.apply(answer) != null)
                .collect(Collectors.groupingBy(answer -> key(answer.taskType())));
        Set<String> taskTypes = new HashSet<>(scoredByTaskType.keySet());
        taskTypes.addAll(expected.keySet());
        List<TaskTypeAverageStep> result = new ArrayList<>();
        for (String taskType : taskTypes) {
            List<ReportScoringAnswerView> scored = scoredByTaskType.getOrDefault(taskType, List.of());
            // An item with no answer row at all was left blank: it counts as 0. A row that exists but has no score
            // (unscored task, AI still running) is not a blank and stays out of the average.
            long blanks = Math.max(0, expected.getOrDefault(taskType, 0) - rowsByTaskType.getOrDefault(taskType, 0L));
            long divisor = scored.size() + blanks;
            if (divisor == 0) {
                continue;
            }
            BigDecimal sum = scored.stream().map(answer -> BigDecimal.valueOf(scoreOf.apply(answer)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new TaskTypeAverageStep(taskType, scored.size(), (int) blanks, sum,
                    sum.divide(BigDecimal.valueOf(divisor), MATH_CONTEXT)));
        }
        result.sort(Comparator.comparing(TaskTypeAverageStep::taskType));
        return result;
    }

    private static SkillTrace weighted(List<ExamDefinitionItemResponse> items, Map<String, BigDecimal> averages,
            Function<ExamDefinitionItemResponse, BigDecimal> weightOf) {
        List<SkillTermStep> terms = new ArrayList<>();
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (ExamDefinitionItemResponse item : items) {
            BigDecimal weight = weightOf.apply(item);
            if (weight == null || weight.signum() <= 0) {
                continue;
            }
            BigDecimal average = averages.get(key(item.taskType()));
            if (average == null) {
                terms.add(new SkillTermStep(item.taskType(), weight, null, null, true));
                continue;
            }
            BigDecimal contribution = weight.multiply(average, MATH_CONTEXT).divide(HUNDRED, MATH_CONTEXT);
            terms.add(new SkillTermStep(item.taskType(), weight, average, contribution, false));
            weightedSum = weightedSum.add(contribution);
            totalWeight = totalWeight.add(weight);
        }
        if (totalWeight.signum() <= 0) {
            return new SkillTrace(terms, weightedSum, totalWeight, null, null, null, false);
        }
        BigDecimal ratio = weightedSum.divide(totalWeight, MATH_CONTEXT);
        BigDecimal scaled = BigDecimal.valueOf(SCALE_FLOOR)
                .add(BigDecimal.valueOf(SCALE_SPAN).multiply(ratio, MATH_CONTEXT));
        return new SkillTrace(terms, weightedSum, totalWeight, ratio, scaled,
                scaled.setScale(0, RoundingMode.HALF_UP).intValue(), true);
    }

    private static String key(String taskType) {
        return TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskType);
    }
}
