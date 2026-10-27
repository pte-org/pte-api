package com.pte.itembank.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code PATCH /questions/{id}/status} body; the reason is required for {@code REJECT}. */
public record QuestionStatusRequest(@NotNull QuestionStatusAction action, @Size(max = 2000) String reason) {
}
