package com.pte.reporting.internal.service;

import com.pte.reporting.domain.enums.Skill;

import java.util.Map;

public record AttemptScoreSummary(SkillScore overall, Map<Skill, SkillScore> skillScores) {
}
