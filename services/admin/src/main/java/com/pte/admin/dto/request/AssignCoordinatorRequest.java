package com.pte.admin.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignCoordinatorRequest(
        @NotNull(message = "Coordinator reference is required")
        UUID assigneePublicId) {
}
