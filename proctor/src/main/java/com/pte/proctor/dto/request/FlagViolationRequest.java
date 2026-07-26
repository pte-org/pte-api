package com.pte.proctor.dto.request;

import com.pte.proctor.domain.enums.ViolationType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FlagViolationRequest(
        @NotNull(message = "Attempt reference is required") UUID attemptPublicId,
        @NotNull(message = "Violation type is required") ViolationType violationType,
        String detail) {
}
