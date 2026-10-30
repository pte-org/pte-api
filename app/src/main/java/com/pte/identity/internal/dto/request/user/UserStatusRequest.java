package com.pte.identity.internal.dto.request.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code PATCH .../status} body. */
public record UserStatusRequest(@NotNull UserStatusAction action, @Size(max = 2000) String reason) {
}
