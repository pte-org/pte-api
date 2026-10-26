package com.pte.attempt.internal.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Explicit candidate navigation request; authorization is enforced from the attempt exam mode. */
public record NavigateTaskRequest(
        @NotNull UUID fromPinnedItemPublicId,
        @NotNull Direction direction) {

    public enum Direction {
        PREVIOUS,
        NEXT
    }
}
