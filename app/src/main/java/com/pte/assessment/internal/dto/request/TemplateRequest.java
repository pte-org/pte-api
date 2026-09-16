package com.pte.assessment.internal.dto.request;

import com.pte.assessment.internal.constant.AssessmentConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Template create/update payload; omit sections for an ACTIVE metadata-only edit. */
public record TemplateRequest(
        @NotBlank(message = AssessmentConstants.TEMPLATE_NAME_REQUIRED) String name,
        @Size(max = 255, message = AssessmentConstants.TEMPLATE_DESCRIPTION_MAX) String description,
        @Valid List<TemplateSectionRequest> sections) {
}
