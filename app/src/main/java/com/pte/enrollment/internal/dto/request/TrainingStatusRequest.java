package com.pte.enrollment.internal.dto.request;

import jakarta.validation.constraints.NotNull;

/** {@code PATCH .../status} body: the lifecycle transition to apply. */
public record TrainingStatusRequest(@NotNull TrainingStatusAction action) {
}
