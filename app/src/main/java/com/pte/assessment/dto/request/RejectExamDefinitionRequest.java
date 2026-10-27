package com.pte.assessment.dto.request;

import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import jakarta.validation.constraints.NotBlank;

public record RejectExamDefinitionRequest(
        @NotBlank(message = ExamDefinitionConstants.TEMPLATE_APPROVAL_REASON_REQUIRED) String reason) {
}
