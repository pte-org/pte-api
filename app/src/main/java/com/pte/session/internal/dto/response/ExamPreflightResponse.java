package com.pte.session.internal.dto.response;

import com.pte.assessment.dto.response.ExamDefinitionFeasibilityResponse;

import java.util.List;

public record ExamPreflightResponse(
        boolean ready,
        ExamDefinitionFeasibilityResponse examDefinition,
        AudiencePreviewResponse audience,
        boolean subscriptionReady,
        boolean windowReady,
        boolean overlapReady,
        List<String> issues) {
}
