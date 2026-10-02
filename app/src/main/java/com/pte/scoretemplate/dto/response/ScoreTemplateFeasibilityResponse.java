package com.pte.scoretemplate.dto.response;

import java.util.List;
import java.util.UUID;

public record ScoreTemplateFeasibilityResponse(
        UUID templatePublicId,
        int templateVersion,
        boolean ready,
        List<ScoreTemplateSlotFeasibilityResponse> slots) {
}
