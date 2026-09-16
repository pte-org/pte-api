package com.pte.assessment.internal.dto.request;

import com.pte.assessment.internal.constant.AssessmentConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TemplateSlotRequest(
        @NotBlank(message = AssessmentConstants.TEMPLATE_SLOT_TASK_TYPE_REQUIRED) String taskType,
        @NotNull(message = AssessmentConstants.TEMPLATE_SLOT_QUESTION_COUNT_REQUIRED) Integer questionCount,
        int orderIndex) {
}
