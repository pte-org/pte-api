package com.pte.enrollment.internal.dto.request;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkAssignStudentsRequest(
        @NotEmpty(message = EnrollmentConstants.AT_LEAST_ONE_STUDENT_REQUIRED)
        List<UUID> studentPublicIds) {
}
