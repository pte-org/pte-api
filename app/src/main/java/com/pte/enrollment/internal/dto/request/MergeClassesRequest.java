package com.pte.enrollment.internal.dto.request;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Moves every student from each source Class into the target (path variable). */
public record MergeClassesRequest(
        @NotEmpty(message = EnrollmentConstants.SOURCE_CLASS_REQUIRED)
        List<UUID> sourceClassPublicIds) {
}
