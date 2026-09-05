package com.pte.scheduling.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkEnrollRequest(
        @NotEmpty(message = "At least one student is required")
        List<UUID> studentPublicIds) {
}
