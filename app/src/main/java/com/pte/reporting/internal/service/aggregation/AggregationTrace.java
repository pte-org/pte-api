package com.pte.reporting.internal.service.aggregation;

import com.pte.reporting.domain.enums.Skill;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The full chain of an attempt's 10-90 scores: task-type averages, then one trace per tested skill and the Overall
 * trace ({@code null} unless all four skills were tested, weighted by the template's {@code overallWeight} column).
 */
public record AggregationTrace(List<TaskTypeAverageStep> taskTypeAverages, Map<Skill, SkillTrace> skills,
                               SkillTrace overall) {

    public AggregationTrace {
        taskTypeAverages = taskTypeAverages == null ? List.of() : List.copyOf(taskTypeAverages);
        skills = skills == null || skills.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(skills));
    }
}
