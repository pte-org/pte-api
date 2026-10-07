package com.pte.practice.internal.dto.request;

import com.pte.practice.internal.constant.PracticeConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PracticeEmailRequest(
        @NotBlank(message = PracticeConstants.EMAIL_REQUIRED)
        @Email(message = PracticeConstants.EMAIL_INVALID)
        String email) {
}
