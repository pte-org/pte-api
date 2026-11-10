package com.pte.proctoring.internal.dto.request;

import com.pte.proctoring.domain.enums.ProctorCommandType;
import com.pte.proctoring.internal.constant.ProctorConstants;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH .../attempts/{attemptPublicId}/status}; FORCE_SUBMIT is the only proctor action. */
public record AttemptStatusActionRequest(
        @NotNull(message = ProctorConstants.COMMAND_TYPE_REQUIRED) ProctorCommandType action) {
}
