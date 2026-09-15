package com.pte.enrollment.internal.dto.request;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignLecturerRequest(
        @NotNull(message = EnrollmentConstants.LECTURER_REFERENCE_REQUIRED)
        UUID assigneePublicId) {
}
