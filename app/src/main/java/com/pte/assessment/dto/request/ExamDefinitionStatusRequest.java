package com.pte.assessment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code PATCH /exam-definitions/{id}/status} body; the reason is required for {@code REJECT}. */
public record ExamDefinitionStatusRequest(@NotNull ExamDefinitionStatusAction action, @Size(max = 2000) String reason) {
}
