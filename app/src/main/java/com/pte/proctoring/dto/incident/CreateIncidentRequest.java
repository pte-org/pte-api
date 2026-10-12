package com.pte.proctoring.dto.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateIncidentRequest(
        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must not exceed 255 characters")
        String title,

        @NotBlank(message = "description is required")
        String description,

        @NotBlank(message = "category is required")
        String category,

        @NotBlank(message = "severity is required")
        String severity,

        UUID studentPublicId,

        UUID attemptPublicId
) {
}
