package com.pte.scoretemplate.dto.request;

import com.pte.scoretemplate.internal.constant.ScoreTemplateConstants;
import jakarta.validation.constraints.NotBlank;

public record RejectScoreTemplateRequest(
        @NotBlank(message = ScoreTemplateConstants.TEMPLATE_APPROVAL_REASON_REQUIRED) String reason) {
}
