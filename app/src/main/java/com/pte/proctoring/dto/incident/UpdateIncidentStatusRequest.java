package com.pte.proctoring.dto.incident;

import jakarta.validation.constraints.NotBlank;

public record UpdateIncidentStatusRequest(
        @NotBlank(message = "status is required")
        String status,

        String notes,

        String receiverRole,

        String resolutionSummary
) {
}
