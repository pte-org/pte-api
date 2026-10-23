package com.pte.practice.internal.dto.request;

import com.pte.practice.internal.constant.PracticeConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PracticeExplanationRequest(
        @NotBlank(message = PracticeConstants.PRACTICE_EXPLANATION_LANGUAGE_REQUIRED)
        @Pattern(regexp = "vi|en", message = PracticeConstants.PRACTICE_EXPLANATION_LANGUAGE_INVALID_MESSAGE)
        String language) {
}
