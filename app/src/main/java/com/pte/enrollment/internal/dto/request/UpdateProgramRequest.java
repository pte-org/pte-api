package com.pte.enrollment.internal.dto.request;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpdateProgramRequest(
        @NotBlank(message = EnrollmentConstants.PROGRAM_NAME_REQUIRED)
        String name,

        String description,

        LocalDate startDate,

        LocalDate endDate) {

    /**
     * Rejected by Bean Validation when both dates are present and endDate
     * is not strictly after startDate.
     */
    @AssertTrue(message = EnrollmentConstants.PROGRAM_END_DATE_BEFORE_START_DATE)
    public boolean isEndDateAfterStartDate() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return endDate.isAfter(startDate);
    }
}
