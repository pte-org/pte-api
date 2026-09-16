package com.pte.enrollment.internal.dto.request;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import jakarta.validation.constraints.NotBlank;

public record UpdateClassRequest(
        @NotBlank(message = EnrollmentConstants.CLASS_NAME_REQUIRED)
        String name) {
}
