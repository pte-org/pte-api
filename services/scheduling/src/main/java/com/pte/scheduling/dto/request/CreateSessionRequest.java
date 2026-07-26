package com.pte.scheduling.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateSessionRequest(
        @NotBlank(message = "Session name is required") String name,
        @NotNull(message = "Snapshot reference is required") UUID snapshotPublicId,
        @NotNull(message = "Open time is required") @Future(message = "Open time must be in the future") Instant opensAt,
        @NotNull(message = "Close time is required") Instant closesAt) {
}
