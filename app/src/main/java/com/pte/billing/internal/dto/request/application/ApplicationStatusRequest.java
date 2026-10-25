package com.pte.billing.internal.dto.request.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code PATCH .../status} body. */
public record ApplicationStatusRequest(@NotNull ApplicationStatusAction action, @Size(max = 2000) String reason) {
}
