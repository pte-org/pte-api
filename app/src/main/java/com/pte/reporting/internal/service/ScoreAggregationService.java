package com.pte.reporting.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.reporting.domain.enums.Skill;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.dto.response.ScoredAnswerView;
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
 * avgRaw=0}. {@code Σw=0} (nothing scored yet for that skill) always
 * reports "insufficient data," never a division by zero.
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

    private static final Map<Skill, Function<ScoreTemplateItemResponse, BigDecimal>> SKILL_WEIGHT_ACCESSORS =
            new EnumMap<>(Skill.class);

    static {
        SKILL_WEIGHT_ACCESSORS.put(Skill.SPEAKING, ScoreTemplateItemResponse::speakingWeight);
        SKILL_WEIGHT_ACCESSORS.put(Skill.WRITING, ScoreTemplateItemResponse::writingWeight);
        SKILL_WEIGHT_ACCESSORS.put(Skill.READING, ScoreTemplateItemResponse::readingWeight);
        SKILL_WEIGHT_ACCESSORS.put(Skill.LISTENING, ScoreTemplateItemResponse::listeningWeight);
    }

    private final ScoringService scoringService;
    private final AttemptService attemptService;
    private final ScoreTemplateService scoreTemplateService;

    public ScoreAggregationService(ScoringService scoringService, AttemptService attemptService,
                                   ScoreTemplateService scoreTemplateService) {
        this.scoringService = scoringService;
        this.attemptService = attemptService;
        this.scoreTemplateService = scoreTemplateService;
    }

    public AttemptScoreSummary aggregate(UUID attemptPublicId, UUID tenantId) {
        return aggregateFromInputs(attemptPublicId, tenantId,
                scoringService.getReportScoringInputsForAttempt(tenantId, attemptPublicId));
    }

    public AttemptScoreSummary aggregateFromInputs(UUID attemptPublicId, UUID tenantId,
            List<ReportScoringAnswerView> answers) {
        AttemptScoreContextView context = attemptService.getScoreContext(attemptPublicId);
        ScoreTemplateResponse template = scoreTemplateService.getByPublicId(context.scoreTemplatePublicId());
        return aggregateWithScores(attemptPublicId, averageSelectedScoresByTaskType(answers), context, template.items());
    }

    /** Loads pinned contexts/templates in batches before aggregating a session cohort. */
    public Map<UUID, ReportScoreAggregation> aggregateBatchFromInputs(Set<UUID> attemptPublicIds,
            Map<UUID, List<ReportScoringAnswerView>> answersByAttempt) {
        if (attemptPublicIds == null || attemptPublicIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AttemptScoreContextView> contexts = attemptService.getScoreContexts(attemptPublicIds);
        Set<UUID> templateIds = contexts.values().stream()
                .map(AttemptScoreContextView::scoreTemplatePublicId).collect(Collectors.toSet());
        Map<UUID, ScoreTemplateResponse> templates = scoreTemplateService.getByPublicIds(templateIds);
        Map<UUID, ReportScoreAggregation> results = new HashMap<>();
        for (UUID attemptPublicId : attemptPublicIds) {
            AttemptScoreContextView context = contexts.get(attemptPublicId);
            ScoreTemplateResponse template = templates.get(context.scoreTemplatePublicId());
            AttemptScoreSummary summary = aggregateWithScores(attemptPublicId,
                    averageSelectedScoresByTaskType(answersByAttempt.getOrDefault(attemptPublicId, List.of())),
                    context, template.items());
            results.put(attemptPublicId, new ReportScoreAggregation(context, summary));
        }
        return Map.copyOf(results);
    }

    /** Preserves the pre-snapshot scoring behavior for reports already published before this workflow. */
    public AttemptScoreSummary aggregateLegacyPublished(UUID attemptPublicId, UUID tenantId) {
        List<ScoredAnswerView> answers = scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId);
        AttemptScoreContextView context = attemptService.getScoreContext(attemptPublicId);
        ScoreTemplateResponse template = scoreTemplateService.getByPublicId(context.scoreTemplatePublicId());
        return aggregateWithScores(attemptPublicId, averageRawScoresByTaskType(answers), context, template.items());
    }

    private AttemptScoreSummary aggregateWithScores(UUID attemptPublicId, Map<String, BigDecimal> avgRawByTaskType,
            AttemptScoreContextView context, List<ScoreTemplateItemResponse> templateItems) {
        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            if (context.testedSections().contains(skill.name())) {
                skillScores.put(skill, computeWeighted(templateItems, avgRawByTaskType, SKILL_WEIGHT_ACCESSORS.get(skill)));
            }
        }

        boolean allFourSkillsTested = Set.of("SPEAKING", "WRITING", "READING", "LISTENING")
                .stream().allMatch(section -> context.testedSections().contains(section));
        SkillScore overall = allFourSkillsTested
                ? computeWeighted(templateItems, avgRawByTaskType, ScoreTemplateItemResponse::overallWeight)
                : null;

        return new AttemptScoreSummary(overall, skillScores);
    }

    private Map<String, BigDecimal> averageRawScoresByTaskType(List<ScoredAnswerView> answers) {
        Map<String, List<ScoredAnswerView>> byTaskType = answers.stream()
                .collect(Collectors.groupingBy(answer ->
                        TaskTypeCodeCompatibility.normalizeTaskTypeKey(answer.taskType())));
        Map<String, BigDecimal> result = new HashMap<>();
        byTaskType.forEach((taskType, group) -> {
            BigDecimal sum = group.stream().map(answer -> BigDecimal.valueOf(answer.rawScore()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(taskType, sum.divide(BigDecimal.valueOf(group.size()), MATH_CONTEXT));
        });
        return result;
    }

    /** Only the selected publishable score contributes; objective scores are supplied unchanged by scoring. */
    private Map<String, BigDecimal> averageSelectedScoresByTaskType(List<ReportScoringAnswerView> answers) {
        Map<String, List<ReportScoringAnswerView>> byTaskType = answers.stream()
                .filter(answer -> answer.selectedScore() != null)
                .collect(Collectors.groupingBy(answer ->
                        TaskTypeCodeCompatibility.normalizeTaskTypeKey(answer.taskType())));
        Map<String, BigDecimal> result = new HashMap<>();
        byTaskType.forEach((taskType, group) -> {
            BigDecimal sum = group.stream()
                    .map(a -> BigDecimal.valueOf(a.selectedScore()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(taskType, sum.divide(BigDecimal.valueOf(group.size()), MATH_CONTEXT));
        });
        return result;
    }

    /** FR-18, applied with whichever weight column {@code weightAccessor} selects (one of the 4 skills, or Overall). */
    private SkillScore computeWeighted(List<ScoreTemplateItemResponse> templateItems,
                                       Map<String, BigDecimal> avgRawByTaskType,
                                       Function<ScoreTemplateItemResponse, BigDecimal> weightAccessor) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (ScoreTemplateItemResponse item : templateItems) {
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
            return SkillScore.insufficientData();
        }
        BigDecimal ratio = weightedSum.divide(totalWeight, MATH_CONTEXT);
        BigDecimal scaled = BigDecimal.valueOf(SCALE_FLOOR).add(BigDecimal.valueOf(SCALE_SPAN).multiply(ratio, MATH_CONTEXT));
        return SkillScore.of(scaled.setScale(0, RoundingMode.HALF_UP).intValue());
    }
}
