package com.pte.session.dto.request;

import com.pte.session.domain.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RecordAttendanceRequest(
        @NotNull(message = "status is required")
        AttendanceStatus status,

        @NotNull(message = "enrollmentPublicId is required")
        UUID enrollmentPublicId,

        @NotNull(message = "expectedVersion is required")
        Long expectedVersion,

        @NotNull(message = "requestPublicId is required")
        UUID requestPublicId,

        @Size(max = 1000, message = "reasonOrNote must not exceed 1000 characters")
        String reasonOrNote
) {
}
