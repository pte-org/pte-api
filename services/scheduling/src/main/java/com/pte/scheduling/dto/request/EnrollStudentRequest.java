package com.pte.scheduling.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrollStudentRequest(@NotNull(message = "Student reference is required") UUID studentPublicId) {
}
