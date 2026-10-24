package com.pte.assessment.dto.response;

public record ExamDefinitionSlotFeasibilityResponse(
        String taskType,
        String section,
        int required,
        long available,
        boolean ready,
        String reason) {
}
