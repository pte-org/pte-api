package com.pte.session.internal.dto.response;

import com.pte.scoretemplate.dto.response.ScoreTemplateFeasibilityResponse;

import java.util.List;

public record ExamPreflightResponse(
        boolean ready,
        ScoreTemplateFeasibilityResponse template,
        AudiencePreviewResponse audience,
        boolean subscriptionReady,
        boolean windowReady,
        boolean overlapReady,
        List<String> issues) {
}
