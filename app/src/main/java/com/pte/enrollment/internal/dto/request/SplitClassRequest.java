package com.pte.enrollment.internal.dto.request;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Creates a new Class under the source's own Program (path variable), then moves the given subset into it. */
public record SplitClassRequest(
        @NotBlank(message = EnrollmentConstants.NEW_CLASS_NAME_REQUIRED) String newClassName,
        @NotEmpty(message = EnrollmentConstants.AT_LEAST_ONE_STUDENT_REQUIRED) List<UUID> studentPublicIds) {
}
