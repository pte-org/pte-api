package com.pte.session.internal.dto.request;

import com.pte.session.internal.constant.SessionConstants;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkEnrollRequest(
        @NotEmpty(message = SessionConstants.AT_LEAST_ONE_STUDENT_REQUIRED)
        List<UUID> studentPublicIds) {
}
