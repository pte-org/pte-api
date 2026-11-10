package com.pte.assessment.internal.service.assembly;

import com.pte.assessment.internal.service.timing.ExamTimePart;

import java.util.List;
import java.util.Map;

/**
 * What the planner decided. When no combination of counts fits every part's window, {@code counts} is empty and
 * {@code failures} names the parts that cannot fit. {@code totalCeilingMet} is false when the plan had to
 * exceed the template's total ceiling because no combination inside every window stays under it.
 */
public record PlanResult(
        Map<String, Integer> counts,
        Map<ExamTimePart, Long> partSeconds,
        long totalSeconds,
        long candidateCombinations,
        boolean totalCeilingMet,
        List<PartFailure> failures) {

    /** A part whose window no combination of counts can reach. */
    public record PartFailure(ExamTimePart part, long reachableMinSeconds, long reachableMaxSeconds,
            long frameMinSeconds, long frameMaxSeconds) {
    }

    public boolean isFeasible() {
        return failures.isEmpty();
    }

    static PlanResult infeasible(List<PartFailure> failures) {
        return new PlanResult(Map.of(), Map.of(), 0, 0, false, List.copyOf(failures));
    }
}
