package com.pte.attempt.internal.dto.request;

import com.pte.attempt.domain.enums.LockdownViolationType;
import com.pte.attempt.internal.constant.AttemptConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RecordSecurityViolationRequest(
        @NotBlank(message = AttemptConstants.SECURITY_CLIENT_EVENT_ID_REQUIRED)
        @Size(max = 128, message = AttemptConstants.SECURITY_CLIENT_EVENT_ID_TOO_LONG)
        String clientEventId,
        @NotNull(message = AttemptConstants.SECURITY_VIOLATION_TYPE_REQUIRED)
        LockdownViolationType violationType,
        Instant clientOccurredAt,
        @Size(max = 2048, message = AttemptConstants.SECURITY_DETAIL_TOO_LONG)
        String detail) {
}
