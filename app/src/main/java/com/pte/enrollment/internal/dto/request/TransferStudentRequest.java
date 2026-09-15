package com.pte.enrollment.internal.dto.request;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Moves a student's existing membership to a different Class â€” may belong to a different Program. */
public record TransferStudentRequest(
        @NotNull(message = EnrollmentConstants.TARGET_CLASS_REFERENCE_REQUIRED)
        UUID targetClassPublicId) {
}
