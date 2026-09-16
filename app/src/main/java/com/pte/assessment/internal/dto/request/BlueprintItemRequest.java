package com.pte.assessment.internal.dto.request;

import com.pte.assessment.internal.constant.AssessmentConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BlueprintItemRequest(
        @NotNull(message = AssessmentConstants.QUESTION_REFERENCE_REQUIRED) UUID questionPublicId,
        @NotBlank(message = AssessmentConstants.SECTION_REQUIRED) String section,
        int orderIndex) {
}
