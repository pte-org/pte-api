package com.pte.enrollment.internal.dto.request;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignStudentRequest(
        @NotNull(message = EnrollmentConstants.STUDENT_REFERENCE_REQUIRED)
        UUID studentPublicId) {
}
