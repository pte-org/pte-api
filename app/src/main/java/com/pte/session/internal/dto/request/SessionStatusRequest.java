package com.pte.session.internal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code PATCH .../status} body. */
public record SessionStatusRequest(@NotNull SessionStatusAction action, @Size(max = 2000) String reason) {
}
