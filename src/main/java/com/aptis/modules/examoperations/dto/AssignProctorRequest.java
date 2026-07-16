package com.aptis.modules.examoperations.dto;

import jakarta.validation.constraints.NotNull;

public record AssignProctorRequest(@NotNull(message = "proctorId is required") Long proctorId) {
}
