package com.pte.assessment.internal.service.assembly;

import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.service.timing.ExamTimePart;
import com.pte.itembank.TaskTypeStimulusService;
import com.pte.itembank.domain.enums.PteSection;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether an exam's counts come from the balanced planner and, if so, runs it. The planner applies only
 * to a template that carries a time frame and only to an exam of all four skills: a part's window describes the
 * whole part, so it means nothing for an exam of one skill.
 */
public final class CountSelector {

    private final TaskTypeStimulusService stimulusService;

    public CountSelector(TaskTypeStimulusService stimulusService) {
        this.stimulusService = stimulusService;
    }

    public CountSelection select(ExamDefinitionResponse template, Set<PteSection> selectedSections, long seed) {
        if (template.timeFrame() == null || !selectedSections.containsAll(Set.of(PteSection.values()))) {
            return CountSelection.notPlanned();
        }
        Map<String, Integer> stimulusSeconds = stimulusSeconds(template);
        List<SlotRequirement> slots = template.items().stream()
                .map(item -> toSlot(item, stimulusSeconds))
                .toList();
        PlanResult plan = BalancedCountPlanner.plan(slots, template.timeFrame(), seed);
        if (plan.isFeasible()) {
            return new CountSelection(plan.counts(), plan, null);
        }
        String reason = plan.failures().stream().map(failure -> failure.part().name())
                .collect(Collectors.joining(",", "TIME_FRAME_INFEASIBLE:", ""));
        return new CountSelection(null, plan, reason);
    }

    private Map<String, Integer> stimulusSeconds(ExamDefinitionResponse template) {
        if (stimulusService == null) {
            return Map.of();
        }
        Set<String> keys = template.items().stream().map(ExamDefinitionItemResponse::taskTypeKey)
                .collect(Collectors.toSet());
        return stimulusService.stimulusBeforeTimer(keys).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().seconds()));
    }

    private static SlotRequirement toSlot(ExamDefinitionItemResponse item, Map<String, Integer> stimulusSeconds) {
        long seconds = (long) item.prepSeconds() + item.responseSeconds()
                + stimulusSeconds.getOrDefault(item.taskTypeKey(), 0);
        return new SlotRequirement(item.taskTypeKey(), ExamTimePart.ofSection(item.section()), item.minCount(),
                item.maxCount(), seconds);
    }
}
