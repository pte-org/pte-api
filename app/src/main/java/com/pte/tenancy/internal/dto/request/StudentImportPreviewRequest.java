package com.pte.tenancy.internal.dto.request;

import com.pte.tenancy.internal.constant.TenancyConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Read-only capacity preview for a planned student import. */
public record StudentImportPreviewRequest(
        @NotNull(message = TenancyConstants.STUDENT_COUNT_REQUIRED)
        @Positive(message = TenancyConstants.STUDENT_COUNT_INVALID)
        Long adding) {
}
