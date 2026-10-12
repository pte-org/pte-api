package com.pte.session.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RecordIdentityVerificationRequest(
        @NotBlank(message = "status is required")
        String status,

        @NotNull(message = "enrollmentPublicId is required")
        UUID enrollmentPublicId,

        @NotNull(message = "expectedVersion is required")
        Long expectedVersion,

        @NotNull(message = "requestPublicId is required")
        UUID requestPublicId,

        @Size(max = 1000, message = "note must not exceed 1000 characters")
        String note
) {
}
