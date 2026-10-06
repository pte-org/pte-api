package com.pte.scoring.dto.request;

import com.pte.scoring.domain.enums.GradingMarkingMode;

import java.util.List;

public record FinalizeGradingCohortRequest(String expectedPreviewVersion, GradingMarkingMode markingMode,
        List<GradingCohortDispositionRequest> outstandingDispositions) {
}
