package com.pte.assessment.internal.dto.request;

import com.pte.assessment.internal.constant.AssessmentConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TemplateSectionRequest(
        @NotBlank(message = AssessmentConstants.TEMPLATE_SECTION_REQUIRED) String section,
        @NotNull(message = AssessmentConstants.TEMPLATE_SECTION_WEIGHT_REQUIRED) Integer weightPercent,
        int orderIndex,
        @Valid List<TemplateSlotRequest> slots) {
}
