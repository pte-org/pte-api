package com.pte.assessment.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Whether a template can be turned into an exam: stock per task type, and the planned duration per part
 * against the template's time frame ({@code totalMaxSeconds} is null without a frame).
 */
public record ExamDefinitionFeasibilityResponse(
        UUID examDefinitionPublicId,
        int examDefinitionVersion,
        boolean ready,
        List<ExamDefinitionSlotFeasibilityResponse> slots,
        List<PartTimeFeasibilityResponse> parts,
        Integer totalMaxSeconds) {

    /** Stock-only report, for callers that do not plan durations. */
    public ExamDefinitionFeasibilityResponse(UUID examDefinitionPublicId, int examDefinitionVersion, boolean ready,
            List<ExamDefinitionSlotFeasibilityResponse> slots) {
        this(examDefinitionPublicId, examDefinitionVersion, ready, slots, List.of(), null);
    }
}
