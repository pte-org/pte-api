package com.pte.scoretemplate.dto.response;

public record ScoreTemplateSlotFeasibilityResponse(
        String taskType,
        String section,
        int required,
        long available,
        boolean ready,
        String reason) {
}
