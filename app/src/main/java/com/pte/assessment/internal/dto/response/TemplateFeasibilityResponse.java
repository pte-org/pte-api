package com.pte.assessment.internal.dto.response;

import java.util.List;
import java.util.UUID;

public record TemplateFeasibilityResponse(
        UUID templatePublicId,
        boolean feasible,
        List<MissingSlot> missingSlots) {

    public record MissingSlot(
            String section,
            String taskType,
            int required,
            long available,
            int missing) {
    }
}
