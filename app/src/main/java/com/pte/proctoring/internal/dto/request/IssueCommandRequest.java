package com.pte.proctoring.internal.dto.request;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.domain.enums.ProctorCommandType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record IssueCommandRequest(
        @NotNull(message = ProctorConstants.ATTEMPT_REFERENCE_REQUIRED) UUID attemptPublicId,
        @NotNull(message = ProctorConstants.COMMAND_TYPE_REQUIRED) ProctorCommandType commandType) {
}
