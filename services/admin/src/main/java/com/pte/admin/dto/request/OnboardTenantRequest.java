package com.pte.admin.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Admin onboards an organization onto the platform. */
public record OnboardTenantRequest(
        @NotBlank(message = "Organization name is required")
        String name,

        @NotBlank(message = "Organization type is required")
        String organizationType,

        @NotBlank(message = "Package name is required")
        String packageName,

        @NotNull(message = "Student limit is required")
        @Min(value = 1, message = "Student limit must be at least 1")
        Integer studentLimit) {
}
