package com.pte.enrollment.internal.dto.request;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** Host adds a Program (Khối/Khóa) under one of its own Organizations. */
public record CreateProgramRequest(
        @NotBlank(message = EnrollmentConstants.PROGRAM_NAME_REQUIRED)
        String name,

        String description,

        LocalDate startDate,

        LocalDate endDate) {

    /**
     * Rejected by Bean Validation when both dates are present and endDate
     * is not strictly after startDate. Named {@code isEndDateAfterStartDate}
     * so the JSON field key in the validation error response is
     * {@code endDateAfterStartDate} (standard Jakarta prefix stripping).
     */
    @AssertTrue(message = EnrollmentConstants.PROGRAM_END_DATE_BEFORE_START_DATE)
    public boolean isEndDateAfterStartDate() {
        if (startDate == null || endDate == null) {
            // Cross-field rule only fires when both are present;
            // nullability is intentionally allowed (dates are optional).
            return true;
        }
        return endDate.isAfter(startDate);
    }
}
