package com.pte.reporting.internal.service.explain;

import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.reporting.internal.dto.response.ItemExplanationResponse;
import com.pte.reporting.internal.dto.response.ScoreExplanationResponse;
import com.pte.reporting.internal.dto.response.SkillExplanationResponse;
import com.pte.reporting.internal.dto.response.SkillTermResponse;
import com.pte.reporting.internal.dto.response.TaskTypeAverageResponse;
import com.pte.reporting.internal.service.AttemptScoreSummary;
import com.pte.reporting.internal.service.ReportSnapshot;
import com.pte.reporting.internal.service.ReportSnapshotCodec;
import com.pte.reporting.internal.service.ReportSnapshotScoreInput;
import com.pte.reporting.internal.service.SkillScore;
import com.pte.reporting.internal.service.TracedAggregation;
import com.pte.reporting.internal.service.aggregation.AggregationTrace;
import com.pte.reporting.internal.service.aggregation.SkillTrace;
import com.pte.scoring.dto.response.ItemScoreTraceView;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.dto.response.RubricParametersView;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Copies published snapshots and live traces into the explanation response; it computes nothing itself. */
final class ScoreExplanationMapper {

    private ScoreExplanationMapper() {
    }

    static ScoreExplanationResponse fromSnapshot(AttemptSummaryView attempt, Instant publishedAt,
            ReportSnapshot snapshot, RubricParametersView parameters) {
        boolean traced = ReportSnapshotCodec.traceAvailable(snapshot);
        List<ItemExplanationResponse> items = snapshot.scoreInputs() == null ? List.of()
                : snapshot.scoreInputs().stream().map(ScoreExplanationMapper::fromSnapshotInput).toList();
        Skills skills = traced ? fromTrace(snapshot.aggregationTrace()) : fromSummary(snapshot.scoreSummary());
        return new ScoreExplanationResponse(attempt.attemptPublicId(), attempt.sessionPublicId(), true, publishedAt,
                snapshot.schemaVersion(), traced, false, snapshot.scoreTemplatePublicId(),
                snapshot.scoreTemplateVersion(), snapshot.rubricVersionPublicId(), parameters, items,
                traced ? averages(snapshot.aggregationTrace()) : List.of(), skills.skills(), skills.overall());
    }

    static ScoreExplanationResponse fromLive(AttemptSummaryView attempt, TracedAggregation traced,
            List<ReportScoringAnswerView> answers, Map<UUID, ItemScoreTraceView> traces,
            RubricParametersView parameters) {
        List<ItemExplanationResponse> items = answers.stream()
                .map(answer -> fromLiveInput(answer, traces.get(answer.answerPublicId()))).toList();
        Skills skills = fromTrace(traced.trace());
        return new ScoreExplanationResponse(attempt.attemptPublicId(), attempt.sessionPublicId(), false, null, null,
                true, traced.includesUnselectedAiScores(), traced.context().examDefinitionPublicId(),
                traced.context().examDefinitionVersion(), traced.template().rubricVersionPublicId(), parameters,
                items, averages(traced.trace()), skills.skills(), skills.overall());
    }

    /** A report published before snapshots existed: nothing to explain. */
    static ScoreExplanationResponse publishedWithoutSnapshot(AttemptSummaryView attempt, Instant publishedAt) {
        return new ScoreExplanationResponse(attempt.attemptPublicId(), attempt.sessionPublicId(), true, publishedAt,
                null, false, false, null, null, null, null, List.of(), List.of(), List.of(), null);
    }

    private static ItemExplanationResponse fromSnapshotInput(ReportSnapshotScoreInput input) {
        Integer score = input.selectedScore();
        return new ItemExplanationResponse(input.answerPublicId(), input.taskType(), input.section(),
                scoreSource(input.scoringMethod(), input.selectedScoreSource(), score), score,
                input.traceKind() == null ? null : input.traceKind().name(),
                input.traitScores() == null ? Map.of() : input.traitScores(), input.wordCount(),
                input.rubricVersionPublicId(), input.objectiveRuleCode(), input.itemTrace());
    }

    private static ItemExplanationResponse fromLiveInput(ReportScoringAnswerView answer, ItemScoreTraceView trace) {
        Integer score = answer.provisionalScore();
        return new ItemExplanationResponse(answer.answerPublicId(), answer.taskType(), answer.section(),
                scoreSource(answer.scoringMethod(), answer.selectedScoreSource(), score), score,
                trace == null ? null : trace.kind().name(), answer.traitScores(), answer.wordCount(),
                answer.rubricVersionPublicId(), trace == null ? null : trace.objectiveRuleCode(),
                trace == null ? null : trace.trace());
    }

    /** OBJECTIVE for answer-key scores, the chosen source otherwise; an unselected live score is the AI's. */
    private static String scoreSource(String scoringMethod, String selectedSource, Integer score) {
        if (score == null) {
            return null;
        }
        if (ReportingConstants.SCORING_METHOD_OBJECTIVE.equals(scoringMethod)) {
            return ReportingConstants.SCORE_SOURCE_OBJECTIVE;
        }
        return selectedSource != null ? selectedSource : ReportingConstants.SCORE_SOURCE_AI;
    }

    private static List<TaskTypeAverageResponse> averages(AggregationTrace trace) {
        return trace.taskTypeAverages().stream().map(step -> new TaskTypeAverageResponse(step.taskType(),
                step.scoredCount(), step.blankCount(), step.sum(), step.average())).toList();
    }

    private static Skills fromTrace(AggregationTrace trace) {
        List<SkillExplanationResponse> skills = new ArrayList<>();
        trace.skills().forEach((skill, skillTrace) -> skills.add(fromSkillTrace(skill.name(), skillTrace)));
        SkillExplanationResponse overall = trace.overall() == null ? null
                : fromSkillTrace(ReportingConstants.OVERALL, trace.overall());
        return new Skills(skills, overall);
    }

    private static SkillExplanationResponse fromSkillTrace(String name, SkillTrace trace) {
        List<SkillTermResponse> terms = trace.terms().stream().map(term -> new SkillTermResponse(term.taskType(),
                term.weight(), term.average(), term.contribution(), term.excluded())).toList();
        return new SkillExplanationResponse(name, trace.score(), trace.sufficientData(), trace.weightedSum(),
                trace.totalWeight(), trace.ratio(), trace.scaledScore(), terms);
    }

    /** Schema-1 snapshot: the published scores only, without the chain. */
    private static Skills fromSummary(AttemptScoreSummary summary) {
        List<SkillExplanationResponse> skills = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            SkillScore score = summary.skillScores() == null ? null : summary.skillScores().get(skill);
            if (score != null) {
                skills.add(fromScore(skill.name(), score));
            }
        }
        SkillExplanationResponse overall = summary.overall() == null ? null
                : fromScore(ReportingConstants.OVERALL, summary.overall());
        return new Skills(skills, overall);
    }

    private static SkillExplanationResponse fromScore(String name, SkillScore score) {
        return new SkillExplanationResponse(name, score.score(), score.sufficientData(), null, score.appliedWeight(),
                null, null, List.of());
    }

    private record Skills(List<SkillExplanationResponse> skills, SkillExplanationResponse overall) {
    }
}
