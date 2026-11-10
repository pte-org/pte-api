package com.pte.reporting.internal.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.internal.service.aggregation.AggregationCore;
import com.pte.reporting.internal.service.aggregation.AggregationTrace;
import com.pte.reporting.internal.service.aggregation.SkillTrace;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes the 10-90 score summary for an attempt, weighted by the exam template pinned to its snapshot (spec
 * FR-18/19/20). Pinned by {@code AttemptService.getScoreContext} (never the currently active template), so activating
 * a new template never changes an already-published attempt's score.
 *
 * <p>All arithmetic lives in {@link AggregationCore}; every method here derives its {@link AttemptScoreSummary} from
 * the core's {@link AggregationTrace}, so the score explanation (FR-07) and the reported figures are the same numbers.
 * Overall is {@code null} — "not applicable", distinct from "insufficient data" — unless all 4 skills were tested.
 */
@Service
public class ScoreAggregationService {

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
        return toSummary(AggregationCore.trace(answers, ReportScoringAnswerView::selectedScore, context, template));
    }

    /**
     * The Host's live view before publication: answers without a chosen score source count by their AI score.
     * Published reports never use this — they keep {@link #aggregate}'s selected-score-only rule.
     */
    public ProvisionalScoreSummary aggregateProvisional(UUID attemptPublicId, UUID tenantId) {
        TracedAggregation traced = aggregateWithTrace(attemptPublicId,
                scoringService.getReportScoringInputsForAttempt(tenantId, attemptPublicId));
        return new ProvisionalScoreSummary(traced.summary(), traced.includesUnselectedAiScores());
    }

    /**
     * The same live (provisional) aggregation as {@link #aggregateProvisional}, over the given inputs, together with
     * its full trace and the pinned context and template: what the score explanation of an unpublished attempt shows.
     */
    public TracedAggregation aggregateWithTrace(UUID attemptPublicId, List<ReportScoringAnswerView> answers) {
        AttemptScoreContextView context = attemptService.getScoreContext(attemptPublicId);
        ExamDefinitionResponse template = examDefinitionService.getByPublicId(context.examDefinitionPublicId());
        AggregationTrace trace = AggregationCore.trace(answers, ReportScoringAnswerView::provisionalScore, context,
                template);
        boolean usesUnselected = answers.stream()
                .anyMatch(answer -> answer.selectedScore() == null && answer.provisionalScore() != null);
        return new TracedAggregation(context, template, toSummary(trace), trace, usesUnselected);
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
            AggregationTrace trace = AggregationCore.trace(answersByAttempt.getOrDefault(attemptPublicId, List.of()),
                    ReportScoringAnswerView::selectedScore, context, template);
            results.put(attemptPublicId, new ReportScoreAggregation(context, toSummary(trace), trace,
                    template.rubricVersionPublicId()));
        }
        return Map.copyOf(results);
    }

    /** The reported summary is read off the trace, never computed a second way. */
    static AttemptScoreSummary toSummary(AggregationTrace trace) {
        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        trace.skills().forEach((skill, skillTrace) -> skillScores.put(skill, toScore(skillTrace)));
        return new AttemptScoreSummary(trace.overall() == null ? null : toScore(trace.overall()), skillScores);
    }

    private static SkillScore toScore(SkillTrace trace) {
        return trace.sufficientData()
                ? SkillScore.of(trace.score(), trace.totalWeight())
                : SkillScore.insufficientData(trace.totalWeight());
    }
}
