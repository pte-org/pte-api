package com.pte.admin.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignLecturerRequest(
        @NotNull(message = "Lecturer reference is required")
        UUID assigneePublicId) {
}
