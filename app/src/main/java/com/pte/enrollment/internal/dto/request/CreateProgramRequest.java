package com.pte.enrollment.internal.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** Host adds a Program (Khá»‘i/KhÃ³a) under one of its own Organizations. */
public record CreateProgramRequest(
        @NotBlank(message = "Program name is required")
        String name,

        String description,

        LocalDate startDate,

        LocalDate endDate) {
}
