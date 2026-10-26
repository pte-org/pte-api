package com.pte.itembank.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code PATCH /task-types/{id}/status} body. */
public record TaskTypeStatusRequest(@NotNull TaskTypeStatusAction action, @Size(max = 2000) String reason) {
}
