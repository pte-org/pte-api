package com.pte.assessment.dto.response;

import java.util.Map;

/**
 * Stock of one task type slot. {@code cefrCounts} says how many of the {@code available} questions sit at each
 * CEFR level ({@code UNCLASSIFIED} for those without one); it is empty when the report does not break stock down.
 */
public record ExamDefinitionSlotFeasibilityResponse(
        String taskType,
        String section,
        int required,
        long available,
        boolean ready,
        String reason,
        Map<String, Long> cefrCounts) {

    /** Stock-only slot, for callers that do not break stock down by CEFR level. */
    public ExamDefinitionSlotFeasibilityResponse(String taskType, String section, int required, long available,
            boolean ready, String reason) {
        this(taskType, section, required, available, ready, reason, Map.of());
    }
}
