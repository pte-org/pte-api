package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateExamStaffProfileRequest(
        @NotBlank(message = IdentityConstants.FULL_NAME_REQUIRED) @Size(max = 255) String fullName,
        @NotBlank(message = IdentityConstants.EMAIL_REQUIRED)
        @Email(message = IdentityConstants.EMAIL_INVALID) @Size(max = 255) String email,
        @Size(max = 255) String phone,
        @PastOrPresent LocalDate dateOfBirth) {
}
