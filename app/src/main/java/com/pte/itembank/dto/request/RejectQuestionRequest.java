package com.pte.itembank.dto.request;

import com.pte.itembank.internal.constant.ItembankConstants;
import jakarta.validation.constraints.NotBlank;

public record RejectQuestionRequest(
        @NotBlank(message = ItembankConstants.QUESTION_REJECTION_REASON_REQUIRED) String reason) {
}
