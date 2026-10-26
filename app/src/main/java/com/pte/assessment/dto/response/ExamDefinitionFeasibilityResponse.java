package com.pte.assessment.dto.response;

import java.util.List;
import java.util.UUID;

public record ExamDefinitionFeasibilityResponse(
        UUID examDefinitionPublicId,
        int examDefinitionVersion,
        boolean ready,
        List<ExamDefinitionSlotFeasibilityResponse> slots) {
}
