package com.pte.practice.internal.dto.request;

import com.pte.practice.internal.constant.PracticeConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record PracticeVerifyRequest(
        @NotNull(message = PracticeConstants.CHALLENGE_ID_REQUIRED)
        UUID challengeId,

        @NotBlank(message = PracticeConstants.EMAIL_REQUIRED)
        @Email(message = PracticeConstants.EMAIL_INVALID)
        String email,

        @NotBlank(message = PracticeConstants.VERIFICATION_CODE_REQUIRED)
        @Pattern(regexp = "\\d{6}", message = PracticeConstants.VERIFICATION_CODE_INVALID)
        String code) {
}
