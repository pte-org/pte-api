package com.pte.scheduling.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignProctorRequest(@NotNull(message = "Proctor reference is required") UUID proctorPublicId) {
}
