package com.pte.assessment.internal.service.assembly;

import com.pte.assessment.internal.constant.AssessmentConstants;

import java.util.Map;

/**
 * The counts the planner chose per task type, or {@code counts == null} when the caller must draw each count
 * on its own; {@code fallbackReason} then says why a template with a time frame could not be planned.
 */
public record CountSelection(Map<String, Integer> counts, PlanResult plan, String fallbackReason) {

    public static CountSelection notPlanned() {
        return new CountSelection(null, null, null);
    }

    public boolean isPlanned() {
        return counts != null;
    }

    /** The report to store with the exam, given the counts that were finally used. */
    public AssemblyReport report(long seed, Map<String, Integer> finalCounts) {
        if (!isPlanned()) {
            return AssemblyReport.independent(AssessmentConstants.DETERMINISTIC_ALGORITHM_VERSION, seed,
                    finalCounts, fallbackReason);
        }
        Map<String, Long> partSeconds = new java.util.TreeMap<>();
        plan.partSeconds().forEach((part, seconds) -> partSeconds.put(part.name(), seconds));
        return new AssemblyReport(AssessmentConstants.BALANCED_ALGORITHM_VERSION, seed,
                AssemblyReport.METHOD_BALANCED, Map.copyOf(finalCounts), Map.copyOf(partSeconds),
                plan.totalSeconds(), plan.candidateCombinations(), plan.totalCeilingMet(), null, null);
    }
}
