package com.pte.reporting.service;

import com.pte.reporting.config.TaskSkillMappingConfig;
import com.pte.reporting.domain.AnswerProjection;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.repository.AnswerProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes the 10–90 score summary for an attempt from its scored answers.
 * Simulation formula, not Pearson's algorithm (phase-08 design constraint):
 * {@code scaledScore = round(10 + percentCorrect * 80)} per skill from its
 * contributing SCORED answers; a skill with zero contributing scored answers
 * reports "insufficient data," never a fabricated score. Overall averages the
 * communicative skills that have data.
 */
@Service
public class ScoreAggregationService {

    private static final int SCALE_FLOOR = 10;
    private static final int SCALE_SPAN = 80;

    private final AnswerProjectionRepository answerProjectionRepository;
    private final TaskSkillMappingConfig taskSkillMappingConfig;

    public ScoreAggregationService(AnswerProjectionRepository answerProjectionRepository,
                                   TaskSkillMappingConfig taskSkillMappingConfig) {
        this.answerProjectionRepository = answerProjectionRepository;
        this.taskSkillMappingConfig = taskSkillMappingConfig;
    }

    @Transactional(readOnly = true)
    public AttemptScoreSummary aggregate(UUID attemptPublicId) {
        List<AnswerProjection> answers = answerProjectionRepository.findByAttemptPublicId(attemptPublicId);

        Map<Skill, List<AnswerProjection>> contributingBySkill = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            contributingBySkill.put(skill, new ArrayList<>());
        }
        for (AnswerProjection answer : answers) {
            if (!answer.isScored()) {
                continue;
            }
            for (Skill skill : taskSkillMappingConfig.skillsFor(answer.getTaskType())) {
                contributingBySkill.get(skill).add(answer);
            }
        }

        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            skillScores.put(skill, computeSkillScore(contributingBySkill.get(skill)));
        }

        return new AttemptScoreSummary(computeOverall(skillScores), skillScores);
    }

    /**
     * Phase 9 correction: every scorer (objective AND AI) reports {@code rawScore}
     * on the SAME 0–100 percentage scale, so a skill fed by a mix of objective
     * and AI-scored answers averages correctly — no per-source special-casing.
     */
    private SkillScore computeSkillScore(List<AnswerProjection> contributing) {
        if (contributing.isEmpty()) {
            return SkillScore.insufficientData();
        }
        double averageRawScore = contributing.stream().mapToInt(AnswerProjection::getRawScore).average().orElse(0);
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
}
