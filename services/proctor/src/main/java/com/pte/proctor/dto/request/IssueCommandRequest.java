package com.pte.proctor.dto.request;

import com.pte.proctor.domain.enums.ProctorCommandType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record IssueCommandRequest(
        @NotNull(message = "Attempt reference is required") UUID attemptPublicId,
        @NotNull(message = "Command type is required") ProctorCommandType commandType) {
}
