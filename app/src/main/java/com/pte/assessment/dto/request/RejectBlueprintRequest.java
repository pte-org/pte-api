package com.pte.assessment.dto.request;

import com.pte.assessment.internal.constant.AssessmentConstants;
import jakarta.validation.constraints.NotBlank;

public record RejectBlueprintRequest(
        @NotBlank(message = AssessmentConstants.BLUEPRINT_REJECTION_REASON_REQUIRED) String reason) {
}
