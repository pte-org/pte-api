package com.pte.enrollment.internal.dto.request;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpdateProgramRequest(
        @NotBlank(message = EnrollmentConstants.PROGRAM_NAME_REQUIRED)
        String name,

        String description,

        LocalDate startDate,

        LocalDate endDate) {
}
