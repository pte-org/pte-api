package com.pte.assessment.internal.service.assembly;

import java.util.Map;

/**
 * How an exam's question counts were decided, stored with the exam so a result can be explained and checked
 * later. {@code method} is BALANCED when the planner fitted the template's time windows and INDEPENDENT when
 * each task type's count was drawn on its own; {@code fallbackReason} says why a template with a time frame
 * still used INDEPENDENT.
 */
public record AssemblyReport(
        String algorithmVersion,
        long seed,
        String method,
        Map<String, Integer> counts,
        Map<String, Long> partSeconds,
        long totalSeconds,
        long candidateCombinations,
        Boolean totalCeilingMet,
        String fallbackReason,
        CefrReport cefr) {

    public static final String METHOD_BALANCED = "BALANCED";
    public static final String METHOD_INDEPENDENT = "INDEPENDENT";

    public static AssemblyReport independent(String algorithmVersion, long seed, Map<String, Integer> counts,
            String fallbackReason) {
        return new AssemblyReport(algorithmVersion, seed, METHOD_INDEPENDENT, Map.copyOf(counts), Map.of(), 0, 0,
                null, fallbackReason, null);
    }

    public AssemblyReport withCefr(CefrReport cefrReport) {
        return new AssemblyReport(algorithmVersion, seed, method, counts, partSeconds, totalSeconds,
                candidateCombinations, totalCeilingMet, fallbackReason, cefrReport);
    }
}
