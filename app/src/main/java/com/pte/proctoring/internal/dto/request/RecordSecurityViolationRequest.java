package com.pte.proctoring.internal.dto.request;

import com.pte.proctoring.domain.enums.LockdownViolationType;
import com.pte.proctoring.internal.constant.ProctorConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RecordSecurityViolationRequest(
        @NotBlank(message = ProctorConstants.SECURITY_CLIENT_EVENT_ID_REQUIRED)
        @Size(max = 128, message = ProctorConstants.SECURITY_CLIENT_EVENT_ID_TOO_LONG)
        String clientEventId,
        @NotNull(message = ProctorConstants.SECURITY_VIOLATION_TYPE_REQUIRED)
        LockdownViolationType violationType,
        Instant clientOccurredAt,
        @Size(max = 2048, message = ProctorConstants.SECURITY_DETAIL_TOO_LONG)
        String detail) {
}
