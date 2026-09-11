package com.pte.admin.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignStudentRequest(
        @NotNull(message = "Student reference is required")
        UUID studentPublicId) {
}
