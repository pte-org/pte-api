package com.pte.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpdateProgramRequest(
        @NotBlank(message = "Program name is required")
        String name,

        String description,

        LocalDate startDate,

        LocalDate endDate) {
}
