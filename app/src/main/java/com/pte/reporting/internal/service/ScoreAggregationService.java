package com.pte.reporting.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotScoringSpec;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.internal.config.TaskSkillMappingConfig;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ScoredAnswerView;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes the 10-90 score summary for an attempt from its scored answers,
 * pulled fresh from {@code scoring} each time (no local copy — see the
 * module's Design Constraints). Simulation formula, not Pearson's algorithm:
 * {@code scaledScore = round(10 + percentCorrect * 80)} per skill from its
 * contributing SCORED answers; a skill with zero contributing scored answers
 * reports "insufficient data," never a fabricated score. Overall averages the
 * communicative skills that have data.
 */
@Service
public class ScoreAggregationService {

    private static final int SCALE_FLOOR = 10;
    private static final int SCALE_SPAN = 80;

    private final ScoringService scoringService;
    private final TaskSkillMappingConfig taskSkillMappingConfig;
    private final AssessmentService assessmentService;

    public ScoreAggregationService(ScoringService scoringService, TaskSkillMappingConfig taskSkillMappingConfig) {
        this(scoringService, taskSkillMappingConfig, null);
    }

    @Autowired
    public ScoreAggregationService(ScoringService scoringService, TaskSkillMappingConfig taskSkillMappingConfig,
                                   AssessmentService assessmentService) {
        this.scoringService = scoringService;
        this.taskSkillMappingConfig = taskSkillMappingConfig;
        this.assessmentService = assessmentService;
    }

    public AttemptScoreSummary aggregate(UUID attemptPublicId, UUID tenantId) {
        return aggregate(attemptPublicId, tenantId, null);
    }

    public AttemptScoreSummary aggregate(UUID attemptPublicId, UUID tenantId, UUID snapshotPublicId) {
        List<ScoredAnswerView> answers = scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId);

        Map<Skill, List<ScoredAnswerView>> contributingBySkill = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            contributingBySkill.put(skill, new ArrayList<>());
        }
        for (ScoredAnswerView answer : answers) {
            for (Skill skill : taskSkillMappingConfig.skillsFor(answer.taskType())) {
                contributingBySkill.get(skill).add(answer);
            }
        }

        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            skillScores.put(skill, computeSkillScore(contributingBySkill.get(skill)));
        }

        SkillScore overall = snapshotPublicId != null && assessmentService != null
                ? computeWeightedOverall(answers, assessmentService.getSnapshotScoringSpec(snapshotPublicId))
                : computeOverall(skillScores);
        return new AttemptScoreSummary(overall, skillScores);
    }

    /**
     * Every scorer (objective AND AI) reports {@code rawScore} on the SAME
     * 0-100 percentage scale, so a skill fed by a mix of objective and
     * AI-scored answers averages correctly — no per-source special-casing.
     */
    private SkillScore computeSkillScore(List<ScoredAnswerView> contributing) {
        if (contributing.isEmpty()) {
            return SkillScore.insufficientData();
        }
        double averageRawScore = contributing.stream().mapToInt(ScoredAnswerView::rawScore).average().orElse(0);
        double percentCorrect = averageRawScore / 100.0;
        return SkillScore.of((int) Math.round(SCALE_FLOOR + percentCorrect * SCALE_SPAN));
    }

    private SkillScore computeOverall(Map<Skill, SkillScore> skillScores) {
        List<Integer> communicativeWithData = skillScores.entrySet().stream()
                .filter(e -> e.getKey().isCommunicative() && e.getValue().sufficientData())
                .map(e -> e.getValue().score())
                .toList();
        if (communicativeWithData.isEmpty()) {
            return SkillScore.insufficientData();
        }
        double average = communicativeWithData.stream().mapToInt(Integer::intValue).average().orElse(0);
        return SkillScore.of((int) Math.round(average));
    }

    private SkillScore computeWeightedOverall(List<ScoredAnswerView> answers, SnapshotScoringSpec spec) {
        if (spec.sectionWeights().isEmpty()) {
            return SkillScore.insufficientData();
        }
        double weightedTotal = 0;
        int totalWeight = 0;
        for (SnapshotScoringSpec.SectionWeight sectionWeight : spec.sectionWeights()) {
            List<ScoredAnswerView> sectionAnswers = answers.stream()
                    .filter(answer -> belongsToSection(answer, sectionWeight.section()))
                    .toList();
            if (sectionAnswers.isEmpty()) {
                return SkillScore.insufficientData();
            }
            double averageRawScore = sectionAnswers.stream().mapToInt(ScoredAnswerView::rawScore).average().orElse(0);
            int sectionScore = (int) Math.round(SCALE_FLOOR + (averageRawScore / 100.0) * SCALE_SPAN);
            weightedTotal += sectionScore * sectionWeight.weightPercent() / 100.0;
            totalWeight += sectionWeight.weightPercent();
        }
        if (totalWeight <= 0) {
            return SkillScore.insufficientData();
        }
        return SkillScore.of((int) Math.round(weightedTotal * 100.0 / totalWeight));
    }

    private boolean belongsToSection(ScoredAnswerView answer, PteSection section) {
        try {
            return com.pte.itembank.domain.enums.PteTaskType.valueOf(answer.taskType()).getSection() == section;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
