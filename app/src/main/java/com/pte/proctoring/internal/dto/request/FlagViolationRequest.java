package com.pte.proctoring.internal.dto.request;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.domain.enums.ViolationType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FlagViolationRequest(
        @NotNull(message = ProctorConstants.ATTEMPT_REFERENCE_REQUIRED) UUID attemptPublicId,
        @NotNull(message = ProctorConstants.VIOLATION_TYPE_REQUIRED) ViolationType violationType,
        String detail) {
}
